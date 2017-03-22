package de.mtc.jira.holiday;

import static de.mtc.jira.holiday.ConfigMap.CF_DAYS;
import static de.mtc.jira.holiday.ConfigMap.CF_END_DATE;
import static de.mtc.jira.holiday.ConfigMap.CF_START_DATE;
import static de.mtc.jira.holiday.ConfigMap.CF_TYPE;
import static de.mtc.jira.holiday.ConfigMap.HR_MANAGER;
import static de.mtc.jira.holiday.ConfigMap.SUPERVISOR_KEY;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.IssueService.IssueResult;
import com.atlassian.jira.bc.issue.IssueService.UpdateValidationResult;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.issue.visibility.Visibilities;
import com.atlassian.jira.bc.issue.worklog.WorklogInputParametersImpl;
import com.atlassian.jira.bc.issue.worklog.WorklogResult;
import com.atlassian.jira.bc.issue.worklog.WorklogService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.query.Query;
import com.opensymphony.workflow.InvalidInputException;
import com.sun.jersey.api.client.ClientResponse;

public abstract class Absence {

	private static final Logger log = LoggerFactory.getLogger(Absence.class);

	private Date startDate;
	private Date endDate;
	private Double numberOfWorkingDays;
	private ApplicationUser user;
	private Timespan timespan;
	private Issue issue;
	private IssueInputParameters issueInputParameters;
	private static final SimpleDateFormat df = new SimpleDateFormat("dd.MM.yy");
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private CustomField cfDays;
	private AbsenceHistory<? extends Absence> history;
	private boolean isHalfDay = false;
	private CustomFieldManager cfm;
	private List<String> errors = new ArrayList<>();

	public static Absence newInstance(Issue issue) throws JiraValidationException, InvalidInputException {
		if (ConfigMap.get("issuetype.sickness").equals(issue.getIssueType().getName())) {
			return new Sickness(issue);
		} else {
			return new Vacation(issue);
		}
	}

	public Absence(Issue issue) throws JiraValidationException, InvalidInputException {
		this.cfm = ComponentAccessor.getCustomFieldManager();

		this.issue = issue;
		this.user = issue.getReporter();

		CustomField cfStart = cfm.getCustomFieldObjectsByName(CF_START_DATE).iterator().next();
		CustomField cfEnd = cfm.getCustomFieldObjectsByName(CF_END_DATE).iterator().next();

		if (cfStart == null || cfEnd == null) {
			throw new JiraValidationException("Field missing");
		}

		this.cfDays = cfm.getCustomFieldObjectsByName(CF_DAYS).iterator().next();
		this.startDate = (Date) issue.getCustomFieldValue(cfStart);
		this.endDate = (Date) issue.getCustomFieldValue(cfEnd);

		if (startDate == null || endDate == null) {
			throw new InvalidInputException("Field \"Start\" and \"Finish\" are required");
		}

		if (startDate.compareTo(endDate) > 0) {
			throw new InvalidInputException("\"Finish\" must be after \"Start\"");
		}

		Date today = AbsenceUtil.today();
		if (startDate.compareTo(today) < 0) {
			throw new InvalidInputException("Start date has already passed");
		}

		this.issueInputParameters = ComponentAccessor.getIssueService().newIssueInputParameters();

		if (cfDays != null) {
			String s = (String) issue.getCustomFieldValue(cfDays);
			try {
				numberOfWorkingDays = Double.valueOf(s);
			} catch (Exception ex) {
				// we have a fallback
				computeNumberOfWorkingDaysFromTimeSpan();
			}
		} else {
			computeNumberOfWorkingDaysFromTimeSpan();
		}
	}

	private final void computeNumberOfWorkingDaysFromTimeSpan() throws JiraValidationException {
		log.debug("Field {} was not set, getting time from tempo", cfDays.getName());
		timespan = new Timespan(user, startDate, endDate);
		log.debug("Number of working days: " + timespan.getNumberOfWorkingDays());
		Double days = Double.valueOf(timespan.getNumberOfWorkingDays());
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		@SuppressWarnings("deprecation")
		CustomField cfType = cfm.getCustomFieldObjectByName(CF_TYPE);
		if (cfType != null) {
			Object type = issue.getCustomFieldValue(cfType);
			if (type != null) {
				isHalfDay = type.toString().contains("Halbe");
			}
		}
		numberOfWorkingDays = (isHalfDay ? 0.5 : 1.0) * days;
	}

	public boolean isHalfDay() {
		return isHalfDay;
	}

	protected IssueInputParameters getIssueInputParameters() {
		return issueInputParameters;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getStartDate() {
		return startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public String getDisplayStartDate() {
		return df.format(startDate);
	}

	public String getDisplayEndDate() {
		return df.format(endDate);
	}

	public void setNumberOfWorkingDays(Double numberOfWorkingDays) {
		this.numberOfWorkingDays = numberOfWorkingDays;
	}

	public Double getNumberOfWorkingDays() {
		return numberOfWorkingDays;
	}

	public ApplicationUser getUser() {
		return user;
	}

	public Issue getIssue() {
		return issue;
	}

	protected AbsenceHistory<? extends Absence> getHistory() throws JiraValidationException {
		if (history == null) {
			history = initHistory();
		}
		return history;
	}

	public String getIssueLink() {
		return ComponentAccessor.getApplicationProperties().getString("jira.baseurl") + "/browse/" + issue.getKey();
	}

	protected abstract AbsenceHistory<? extends Absence> initHistory() throws JiraValidationException;

	private Long getOriginalEstimate() throws JiraValidationException {
		long result = 0;
		if (timespan == null) {
			timespan = new Timespan(user, startDate, endDate);
		}
		JSONArray allDays = timespan.getWorkingDays();
		int length = allDays.length();
		for (int i = 0; i < length; i++) {
			try {
				JSONObject day = allDays.getJSONObject(i);
				if ("WORKING_DAY".equals(day.get("type"))) {
					result += day.getInt("requiredSeconds");
				}
			} catch (Exception e) {
				log.error("Error setting worklog", e);
			}
		}
		return isHalfDay ? result / 2 : result;
	}

	public void updateFieldValues() throws JiraValidationException {
		long seconds = getOriginalEstimate();
		String inMinutes = String.valueOf(seconds / 60) + "m";
		log.debug("Setting original estimate to {}", inMinutes);
		issueInputParameters.setOriginalAndRemainingEstimate(inMinutes, inMinutes);
		issueInputParameters.addCustomFieldValue(cfDays.getId(), String.valueOf(numberOfWorkingDays));
	}

	public void updateIssue() {
		log.debug("Updating issue {}", issue.getKey());

		IssueService issueService = ComponentAccessor.getIssueService();
		ApplicationUser currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
		UpdateValidationResult validationResult = issueService.validateUpdate(currentUser, issue.getId(),
				issueInputParameters);

		if (validationResult.isValid()) {
			IssueResult result = issueService.update(currentUser, validationResult);
			if (!result.isValid()) {
				log.error("Errors occured while updating issue " + issue.getKey() + " " + result.getErrorCollection());
			} else {
				log.info("Updated issue " + issue.getKey());
			}
		} else {
			log.error("Unable to update issue: Invalid validation result: " + validationResult.getErrorCollection());
		}

		log.debug("After assignee = {}", issue.getAssigneeId());
	}

	public double getVacationDaysOfThisYear() throws JiraValidationException {
		return getHistory().getSum();
	}

	private void validateConflicts() throws JiraValidationException, InvalidInputException {

		String issueType = issue.getIssueType().getName();
		// String jqlQuery = "type=issueType and reporter = currentUser and
		// status != \"Rejected\" and status != \"Closed\" and
		// ((\"Start\">=startDate and \"Start\"<=endDate) or
		// (\"Finish\">=startDate and \"Finish\"<=endDate))";
		// jqlQuery = jqlQuery.replace("issueType", "\"" + issueType + "\"")
		// .replaceAll("currentUser", "\"" + getUser().getKey() + "\"")
		// .replaceAll("startDate", dateFormat.format(startDate))
		// .replaceAll("endDate", dateFormat.format(endDate));

		// log.debug("Parsing query {}", jqlQuery);
		// SearchService.ParseResult parseResult =
		// searchService.parseQuery(getUser(), jqlQuery);

		SearchService searchService = ComponentAccessor.getComponent(SearchService.class);

		JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();

		Long startId = cfm.getCustomFieldObjectByName("Start").getIdAsLong();
		Long endId = cfm.getCustomFieldObjectByName("Finish").getIdAsLong();

		Query query = builder.where().issueType(issueType).and().reporterIsCurrentUser().and().status()
				.notIn("Rejected", "Closed").and().sub().sub().customField(startId).gtEq(dateFormat.format(startDate))
				.and().customField(startId).ltEq(dateFormat.format(endDate)).endsub().or().sub().customField(endId)
				.gtEq(dateFormat.format(startDate)).and().customField(endId).ltEq(dateFormat.format(endDate)).endsub()
				.endsub().buildQuery();

		log.debug("Validation: {}", searchService.getJqlString(query));

		// if (!parseResult.isValid()) {
		// log.debug("Invalid parse result {}", parseResult.getErrors());
		// }
		// Query query = parseResult.getQuery();
		SearchResults results = null;
		try {
			results = searchService.search(getUser(), query, new com.atlassian.jira.web.bean.PagerFilter<>());
		} catch (SearchException e) {
			throw new JiraValidationException("Search Exception", e);
		}
		List<Issue> issues = results.getIssues();
		log.debug("Issues: " + issues);
		if (issues != null && !issues.isEmpty()) {
			Issue conflictingIssue = issues.get(0);
			if (this.issue != null && this.issue.getKey() != null
					&& this.issue.getKey().equals(conflictingIssue.getKey())) {
				return;
			}
			CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
			CustomField cfStart = cfm.getCustomFieldObjectsByName(CF_START_DATE).iterator().next();
			CustomField cfEnd = cfm.getCustomFieldObjectsByName(CF_END_DATE).iterator().next();
			Date start = (Date) conflictingIssue.getCustomFieldValue(cfStart);
			Date end = (Date) conflictingIssue.getCustomFieldValue(cfEnd);
			StringBuilder sb = new StringBuilder();
			sb.append("Für diese Zeitspanne ist bereits eine Abwesenheit beantragt.");
			sb.append("Issue " + conflictingIssue.getKey());
			sb.append(", Start: " + df.format(start));
			sb.append(", Ende: " + df.format(end));
			throw new InvalidInputException(sb.toString());
		}
	}

	public void validate() throws InvalidInputException, JiraValidationException {
		if (getStartDate().compareTo(getEndDate()) > 0) {
			throw new InvalidInputException("End date must be before start date");
		}
		validateConflicts();
	}

	public void setPlanitems() throws PlanItemException {
		PlanItemManager manager = new PlanItemManager(issue, isHalfDay ? 50 : 100);
		manager.setTimespan(dateFormat.format(startDate), dateFormat.format(endDate));
		ClientResponse response = manager.createPlanItem();
		int status = response.getStatus();
		if (!(status == 200 || status == 201)) {
			throw new PlanItemException("Unable to set plan items, status code was: " + status + " response");
		}
		log.info("Successfully created plan item for issue {}", issue);
	}

	public void deletePlanitems() throws PlanItemException, JiraValidationException {
		PlanItemManager manager = new PlanItemManager(issue, isHalfDay ? 50 : 100);
		manager.setTimespan(dateFormat.format(startDate), dateFormat.format(endDate));
		ClientResponse response = manager.deletePlanItems();
		int status = response.getStatus();
		if (!(status == 200 || status == 201)) {
			throw new PlanItemException("Unable to delete plan items, status code was: " + status + " response");
		}
		log.info("Successfully deleted plan item for issue {}", issue);
	}

	public abstract void writeVelocityComment(boolean finalApproval) throws JiraValidationException;

	public void deleteComments() {
		ComponentAccessor.getCommentManager().deleteCommentsForIssue(issue);
	}

	public void setWorkLog() throws JiraValidationException {

		JiraServiceContext jiraServiceContext = new JiraServiceContextImpl(user);
		WorklogService worklogService = ComponentAccessor.getComponent(WorklogService.class);
		if (timespan == null) {
			timespan = new Timespan(user, startDate, endDate);
		}
		JSONArray allDays = timespan.getWorkingDays();
		int length = allDays.length();
		for (int i = 0; i < length; i++) {
			try {
				JSONObject day = allDays.getJSONObject(i);
				if ("WORKING_DAY".equals(day.get("type"))) {
					int seconds = day.getInt("requiredSeconds");
					if (isHalfDay) {
						seconds = seconds / 2;
					}
					String timeSpent = String.valueOf(seconds / 60) + "m";
					Date date = dateFormat.parse(day.getString("date"));
					final WorklogInputParametersImpl.Builder builder = WorklogInputParametersImpl.issue(issue)
							.timeSpent(timeSpent).startDate(date)
							.comment("Automatically created from MTC Absence plugin")
							.visibility(Visibilities.publicVisibility());

					log.debug("Workflow permissions: {}",
							worklogService.hasPermissionToCreate(jiraServiceContext, issue, true));

					WorklogResult result = worklogService.validateCreate(jiraServiceContext, builder.build());

					log.debug("Creating worklog for issue {} and user {}, time spent: {}, start date: {}", issue, user,
							timeSpent, startDate);
					log.debug("Result: {}", result);
					log.debug("Editable: {}", result);
					Worklog worklog = worklogService.createAndAutoAdjustRemainingEstimate(jiraServiceContext, result,
							true);
					log.debug("Created worklog {}, {} on issue {}", worklog, timeSpent, issue);
				}
			} catch (Exception e) {
				log.error("Error setting worklog", e);
			}
		}
	}

	public void deleteWorklogs() {
		WorklogManager worklogManager = ComponentAccessor.getWorklogManager();
		List<Worklog> worklogs = worklogManager.getByIssue(issue);
		JiraServiceContext jiraServiceContext = new JiraServiceContextImpl(user);
		WorklogService worklogService = ComponentAccessor.getComponent(WorklogService.class);
		for (Worklog worklog : worklogs) {
			log.debug("Deleting worklog for issue", worklog.getStartDate(), worklog.getTimeSpent());
			WorklogResult worklogResult = worklogService.validateDelete(jiraServiceContext, worklog.getId());
			worklogService.deleteAndAutoAdjustRemainingEstimate(jiraServiceContext, worklogResult, true);
		}
	}

	public ApplicationUser getSupervisor() throws JiraValidationException {
		PropertyHelper props = new PropertyHelper(issue.getReporter());
		String supervisorName = props.exists(SUPERVISOR_KEY) ? String.valueOf(props.get(SUPERVISOR_KEY)) : "teamlead";
		if (supervisorName == null || supervisorName.isEmpty()) {
			supervisorName = "teamlead";
		}
		ApplicationUser supervisor = ComponentAccessor.getUserManager().getUserByName(supervisorName);
		if (supervisor == null) {
			throw new JiraValidationException("No supervisor defined for user: " + user);
		}
		return supervisor;
	}

	public void assignToSuperVisor() throws JiraValidationException {
		ApplicationUser supervisor = getSupervisor();
		issueInputParameters.setAssigneeId(supervisor.getId().toString());
		log.info("Assignee for issue {} is set to: {}", issue.getKey(), supervisor.getName());
	}

	public ApplicationUser getHumanResourcesManager() throws JiraValidationException {
		ApplicationUser manager = ComponentAccessor.getUserManager().getUserByName(HR_MANAGER);
		if (manager == null) {
			throw new JiraValidationException("No hr manager defined for user: " + user);
		}
		return manager;
	}

	public void assignToHumanResourceManager() throws JiraValidationException {
		ApplicationUser manager = getHumanResourcesManager();
		issueInputParameters.setAssigneeId(manager.getName());
		log.info("Assignee for issue {} is set to: {}", issue.getKey(), manager.getName());
	}

	private void addVelocityContextParams(Class<?> clazz, Map<String, Object> map) {
		for (Field field : clazz.getDeclaredFields()) {
			try {
				field.setAccessible(true);
				Object value = field.get(this);
				if (value instanceof Date) {
					value = df.format(value);
				}
				map.put(field.getName(), value);
			} catch (Exception e) {
				log.error("Couldn't create velocity context params", e);
			}
		}
	}

	public Map<String, Object> getVelocityContextParams() {
		Map<String, Object> result = new HashMap<>();
		result.put("issueKey", issue.getKey());
		addVelocityContextParams(Absence.class, result);
		addVelocityContextParams(this.getClass(), result);
		return result;
	}
}
