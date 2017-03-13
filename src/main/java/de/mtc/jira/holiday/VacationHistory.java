package de.mtc.jira.holiday;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.user.ApplicationUser;

public class VacationHistory {

	private static final Logger log = LoggerFactory.getLogger(VacationHistory.class);
	private static final String JQL = ConfigMap.get("holiday-history.jqlquery");
	private ApplicationUser user;
	private List<Vacation> vacations;
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	public static VacationHistory getHistory(Vacation vac) throws JiraValidationException {
		VacationHistory history = new VacationHistory(vac.getUser());
		ArrayList<Vacation> vacations = new ArrayList<>();
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int year = cal.get(Calendar.YEAR);
		cal.set(year, 0, 1);
		Date startOfYear = cal.getTime();
		for (Issue issue : history.getRelevantIssues()) {
			try {
				Vacation entry = new Vacation(issue);
				if (startOfYear.compareTo(entry.getEndDate()) > 0) {
					log.debug("Not adding entry {} > {}", startOfYear, entry.getEndDate());
					continue;
				} else if (startOfYear.compareTo(entry.getStartDate()) > 0) {
					entry.setStartDate(startOfYear);
				}
				vacations.add(entry);
			} catch (Exception e) {
				log.error("Unable to get entry for issue {}, {}", issue, e.getMessage());
			}
		}
		history.setVacations(vacations);
		return history;
	}

	public VacationHistory(ApplicationUser user) {
		this.user = user;
	}

	public void setVacations(List<Vacation> vacations) {
		this.vacations = vacations;
	}
	
	public int getCurrentYear() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int year = cal.get(Calendar.YEAR);
		return year;
	}

	private final List<Issue> getRelevantIssues() throws JiraValidationException {

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

	public List<Vacation> getVacations() {
		return vacations;
	}

	public Double getNumberOfPreviousHolidays() {
		double d = 0d;
		for (Vacation entry : vacations) {
			d += entry.getNumberOfWorkingDays();
		}
		return d;
	}
}
