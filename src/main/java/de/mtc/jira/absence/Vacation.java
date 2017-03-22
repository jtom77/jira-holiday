package de.mtc.jira.absence;

import static de.mtc.jira.absence.ConfigMap.CF_ANNUAL_LEAVE;
import static de.mtc.jira.absence.ConfigMap.CF_TYPE;
import static de.mtc.jira.absence.ConfigMap.PROP_ANNUAL_LEAVE;

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
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.query.Query;
import com.atlassian.query.order.SortOrder;
import com.atlassian.velocity.VelocityManager;
import com.opensymphony.workflow.InvalidInputException;

public class Vacation extends Absence {

	private static final Logger log = LoggerFactory.getLogger(Vacation.class);

	private String type;
	boolean isHalfDay;
	private double annualLeave;
	private CustomField cfAnnualLeave;

	public Vacation(Issue issue) throws JiraValidationException, InvalidInputException {
		super(issue);
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		CustomField cfType = cfm.getCustomFieldObjectsByName(CF_TYPE).iterator().next();
		this.cfAnnualLeave = cfm.getCustomFieldObjectsByName(CF_ANNUAL_LEAVE).iterator().next();
		this.type = (String) issue.getCustomFieldValue(cfType).toString();
		this.isHalfDay = type.contains("Halbe");
		this.annualLeave = new PropertyHelper(getUser()).getDouble(PROP_ANNUAL_LEAVE);
	}

	public String getType() {
		return type;
	}

	public double getAnnualLeave() {
		return annualLeave;
	}

	@Override
	public void updateFieldValues() throws JiraValidationException {
		super.updateFieldValues();
		getIssueInputParameters().addCustomFieldValue(cfAnnualLeave.getId(), String.valueOf(annualLeave));
	}

	@Override
	public void validate() throws InvalidInputException, JiraValidationException {
		super.validate();
		if (annualLeave - getVacationDaysOfThisYear() - getNumberOfWorkingDays() < 0) {
			throw new InvalidInputException("There are not enough vacation days left");
		}
	}

	public void writeVelocityComment(boolean finalApproval) throws JiraValidationException {
		VelocityManager manager = ComponentAccessor.getVelocityManager();
		Map<String, Object> contextParameters = new HashMap<>();
		AbsenceHistory<Vacation> history = initHistory();
		contextParameters.put("vacations",
				history.getAbsences().stream().map(t -> t.getVelocityContextParams()).collect(Collectors.toList()));
		contextParameters.put("vacation", getVelocityContextParams());
		contextParameters.put("currentYear", AbsenceUtil.getCurrentYear());
		double previous = getVacationDaysOfThisYear();
		double wanted = getNumberOfWorkingDays();
		double rest = annualLeave - previous;
		double restAfter = rest - wanted;
		contextParameters.put("previous", previous);
		contextParameters.put("wanted", wanted);
		contextParameters.put("rest", rest);
		contextParameters.put("restAfter", restAfter);

		String baseUrl = ComponentAccessor.getComponent(JiraBaseUrls.class).baseUrl();
		contextParameters.put("vacationwatcher", baseUrl + "/secure/VacationWatcher.jspa");

		String template = finalApproval ? "comment_approved.vm" : "comment.vm";
		getIssueInputParameters().setComment(manager.getBody("templates/comment/", template, contextParameters));
	}

	@Override
	protected AbsenceHistory<Vacation> initHistory() throws JiraValidationException {
		HistoryParams<Vacation> params = new HistoryParams<Vacation>() {

			@Override
			public List<Vacation> filter(List<Issue> issues) {
				List<Vacation> vacations = new ArrayList<>();
				Calendar cal = Calendar.getInstance();
				cal.setTime(new Date());
				int year = cal.get(Calendar.YEAR);
				cal.set(year, 0, 1);
				Date startOfYear = cal.getTime();
				for (Issue issue : issues) {
					try {
						Vacation vacation = new Vacation(issue);
						if (startOfYear.compareTo(vacation.getEndDate()) > 0) {
							log.debug("Not adding entry {} > {}", startOfYear, vacation.getEndDate());
							continue;
						} else if (startOfYear.compareTo(vacation.getStartDate()) > 0) {
							vacation.setStartDate(startOfYear);
							Timespan timespan = new Timespan(getUser(), startOfYear, vacation.getEndDate());
							double numWorkingDays = timespan.getNumberOfWorkingDays();
							log.debug("Setting numberOfWorkingDays {}", numWorkingDays);
							vacation.setNumberOfWorkingDays((vacation.isHalfDay() ? 0.5 : 1.0) * numWorkingDays);
						}
						vacations.add(vacation);
					} catch (Exception e) {
						log.error("Unable to get entry for issue {}, {}", issue, e.getMessage());
					}
				}
				return vacations;
			}

			@Override
			public Query getJqlQuery() {

				 Long startId =
				 AbsenceUtil.getCustomField(AbsenceUtil.START_FIELD_NAME).getIdAsLong();
				 Long endId =
				 AbsenceUtil.getCustomField(AbsenceUtil.END_FIELD_NAME).getIdAsLong();
				
				 Query query =
				 JqlQueryBuilder.newBuilder().where().issueType("Urlaubsantrag").and().reporter()
				 .in(getUser().getKey(),
				 getUser().getName()).and().customField(endId)
				 .gtEq(AbsenceUtil.formatDate(AbsenceUtil.startOfYear())).endWhere().orderBy()
				 .addSortForFieldName(AbsenceUtil.START_FIELD_NAME,
				 SortOrder.ASC, true).buildQuery();

				String jqlQuery = "type=\"Urlaubsantrag\" and reporter={user} and \"Finish\" > startOfYear() and status = \"Approved\" order by \"Start\"";
				jqlQuery = jqlQuery.replace("{user}", "\"" + getUser().getKey() + "\"");
				SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
				SearchService.ParseResult parseResult = searchService.parseQuery(getUser(), jqlQuery);
				return parseResult.getQuery();
			}
		};

		return AbsenceHistory.getHistory(getUser(), params);

	}
}