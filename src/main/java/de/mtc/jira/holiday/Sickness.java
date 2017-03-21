package de.mtc.jira.holiday;

import static de.mtc.jira.holiday.ConfigMap.CF_SICKNESS_TYPE;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.query.Query;
import com.atlassian.velocity.VelocityManager;
import com.opensymphony.workflow.InvalidInputException;

public class Sickness extends Absence {

	private static final Logger log = LoggerFactory.getLogger(Sickness.class);

	private boolean kindkrank = false;

	private String type;

	public Sickness(Issue issue) throws JiraValidationException {
		super(issue);
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		CustomField cfType = cfm.getCustomFieldObjectsByName(CF_SICKNESS_TYPE).iterator().next();
		this.type = (String) issue.getCustomFieldValue(cfType).toString();
		this.kindkrank = type.contains("Kind");
	}

	public boolean isKindkrank() {
		return kindkrank;
	}

	public String getType() {
		return type;
	}

	@Override
	public void validate() throws InvalidInputException, JiraValidationException {
		if (getStartDate().compareTo(getEndDate()) > 0) {
			throw new InvalidInputException("End date must be before start date");
		}
	}

	@Override
	public void writeVelocityComment(boolean finalApproval) throws JiraValidationException {
		VelocityManager manager = ComponentAccessor.getVelocityManager();
		Map<String, Object> contextParameters = new HashMap<>();
		AbsenceHistory<Sickness> history = initHistory();
		contextParameters.put("vacations",
				history.getAbsences().stream().map(t -> t.getVelocityContextParams()).collect(Collectors.toList()));
		contextParameters.put("vacation", getVelocityContextParams());
		contextParameters.put("currentYear", AbsenceUtil.getCurrentYear());
		double previous = getVacationDaysOfThisYear();
		double wanted = getNumberOfWorkingDays();
		contextParameters.put("previous", previous);
		contextParameters.put("wanted", wanted);
		getIssueInputParameters()
				.setComment(manager.getBody("templates/comment/", "sickness_comment_view.vm", contextParameters));
	}

	@Override
	protected AbsenceHistory<Sickness> initHistory() throws JiraValidationException {

		HistoryParams<Sickness> params = new HistoryParams<Sickness>() {

			@Override
			public List<Sickness> filter(List<Issue> issues) {
				List<Sickness> vacations = new ArrayList<>();
				Calendar cal = Calendar.getInstance();
				cal.setTime(new Date());
				int year = cal.get(Calendar.YEAR);
				cal.set(year, 0, 1);
				Date startOfYear = cal.getTime();

				for (Issue issue : issues) {
					try {
						Sickness sickness = new Sickness(issue);
						if (isKindkrank() != sickness.isKindkrank()
								|| (startOfYear.compareTo(sickness.getEndDate()) > 0)) {
							log.debug("Not adding entry {} > {}", startOfYear, sickness.getEndDate());
							continue;
						} else if (startOfYear.compareTo(sickness.getStartDate()) > 0) {
							sickness.setStartDate(startOfYear);
						}
						vacations.add(sickness);
					} catch (Exception e) {
						log.error("Unable to get entry for issue {}, {}", issue, e.getMessage());
					}
				}
				return vacations;
			}

			@Override
			public Query getJqlQuery() {
				String jqlQuery = "type=\"Sickness\" and reporter={user} and \"Finish\" > startOfYear() and status = \"Closed\"";
				jqlQuery = jqlQuery.replace("{user}", "\"" + getUser().getKey() + "\"");
				SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
				SearchService.ParseResult parseResult = searchService.parseQuery(getUser(), jqlQuery);
				return parseResult.getQuery();
			}
		};

		return AbsenceHistory.getHistory(getUser(), params);
	}
}
