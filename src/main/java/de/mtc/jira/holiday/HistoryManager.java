package de.mtc.jira.holiday;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.user.ApplicationUser;

public class HistoryManager {

	private static final Logger log = LoggerFactory.getLogger(HistoryManager.class);
	// private static final String JQL = "type = Urlaubsantrag AND
	// reporter={reporter} AND status='APPROVED' AND created > {created}";
	private static final String JQL = ConfigMap.get("holiday-history.jqlquery");
	private static final String CF_START = "Start";
	private static final String CF_END = "Finish";
	private static final String CF_TYPE = "Urlaubstyp";
	private ApplicationUser user;
	private List<HistoryEntry> historyEntries;
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	public HistoryManager(ApplicationUser user) {
		this.user = user;
	}

	private List<Issue> getRelevantIssues() throws JiraValidationException {

		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int year = cal.get(Calendar.YEAR);
		cal.set(year - 1, 0, 1);
		String createdAfter = dateFormat.format(cal.getTime());
		String jqlQuery = JQL.replace("{reporter}", user.getName()).replace("{created}", createdAfter);

		log.debug("Exceuting jql query {}", jqlQuery);

		List<Issue> result = new ArrayList<>();
		SearchService searchService = ComponentAccessor.getComponentOfType(SearchService.class);
		SearchService.ParseResult parseResult = searchService.parseQuery(user, jqlQuery);
		if (parseResult.isValid()) {
			SearchResults results = null;
			try {
				results = searchService.search(user, parseResult.getQuery(),
						new com.atlassian.jira.web.bean.PagerFilter<>());
			} catch (SearchException e) {
				throw new JiraValidationException("Couldn't retrieve old issues", e);
			}
			if (results != null) {
				final List<Issue> issues = results.getIssues();
				result.addAll(issues);
				String issueList = issues.stream().map(t -> t.getKey()).collect(Collectors.joining(","));
				log.debug("Result " + issueList);
			}
		} else {
			log.debug("Search result not valid " + parseResult.getErrors());
		}

		log.debug("Found results: {}", result);

		return result;
	}

	private CustomField getCustomField(Issue issue, String fieldName, String... fieldNameFallbacks)
			throws JiraValidationException {
		CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
		CustomField cf = customFieldManager.getCustomFieldObjectByName(fieldName);
		if (cf != null) {
			return cf;
		}
		for (String fallBack : fieldNameFallbacks) {
			cf = customFieldManager.getCustomFieldObjectByName(fallBack);
			if (cf != null) {
				return cf;
			}
		}
		throw new JiraValidationException(
				"Field not found " + fieldName + ", fallbacks: " + Arrays.toString(fieldNameFallbacks));
	}

	private Date getDateFromField(Issue issue, String fieldName, String... fieldNameFallbacks) throws Exception {
		CustomField cf = getCustomField(issue, fieldName, fieldNameFallbacks);
		Object value = issue.getCustomFieldValue(cf);
		if (value instanceof Date) {
			return (Date) value;
		}
		return dateFormat.parse(value.toString());
	}

	private HistoryEntry getEntry(Issue issue) throws Exception {
		Date start = getDateFromField(issue, CF_START, WorkflowHelper.CF_START_DATE);
		Date end = getDateFromField(issue, CF_END, WorkflowHelper.CF_END_DATE);
		CustomField cfType = getCustomField(issue, CF_TYPE, WorkflowHelper.CF_TYPE);
		String type = issue.getCustomFieldValue(cfType).toString();
		return new HistoryEntry(start, end, type.contains("Halbe"), issue.getKey());
	}

	public void computeEntries() throws Exception {
		historyEntries = new ArrayList<>();
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int year = cal.get(Calendar.YEAR);
		cal.set(year, 0, 1);
		Date startOfYear = cal.getTime();

		for (Issue issue : getRelevantIssues()) {
			try {
				HistoryEntry entry = getEntry(issue);
				if (startOfYear.compareTo(entry.end) > 0) {
					log.debug("Not adding entry {} > {}", startOfYear, entry.end);
					continue;
				} else if (startOfYear.compareTo(entry.start) > 0) {
					entry.start = startOfYear;
				}
				historyEntries.add(entry);
			} catch (Exception e) {
				log.error("Unable to get entry for issue {}, {}", issue, e.getMessage());
			}
		}
	}
	
	public Double getNumberOfPreviousHolidays() {
		double d = 0d;
		for(HistoryEntry entry : historyEntries) {
			d += entry.numberOfDays;
		}
		return d;
	}
	

	public String getComment() {
		StringBuilder sb = new StringBuilder();
		sb.append("||Von||Bis||Typ||Tage||");
		for (HistoryEntry entry : historyEntries) {
			sb.append("\n").append(entry.toString());
		}
		return sb.toString();
	}

	class HistoryEntry {

		Date start;
		Date end;
		Double numberOfDays;
		boolean isHalfDay;
		String issueKey;

		HistoryEntry(Date start, Date end, boolean isHalfDay, String issueKey) throws JiraValidationException {
			this.start = start;
			this.end = end;
			this.isHalfDay = isHalfDay;
			TimeSpan timespan = new TimeSpan(user, start, end);
			numberOfDays = Double.valueOf(timespan.getNumberOfWorkingDays());
			if (isHalfDay) {
				this.numberOfDays = 0.5 * numberOfDays;
			}
			this.issueKey = issueKey;
		}

		public String toString() {
			return String.format("|%s|%s|%s|%s|%d|", issueKey, dateFormat.format(start), dateFormat.format(end),
					isHalfDay ? "Halbe Tage" : "Ganze Tage", numberOfDays);
		}
	}
}
