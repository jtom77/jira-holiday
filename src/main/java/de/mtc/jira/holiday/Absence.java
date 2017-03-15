package de.mtc.jira.holiday;

import static de.mtc.jira.holiday.ConfigMap.CF_DAYS;
import static de.mtc.jira.holiday.ConfigMap.CF_END_DATE;
import static de.mtc.jira.holiday.ConfigMap.CF_START_DATE;
import static de.mtc.jira.holiday.ConfigMap.HR_MANAGER;
import static de.mtc.jira.holiday.ConfigMap.SUPERVISOR_KEY;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
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
import com.atlassian.jira.bc.issue.visibility.Visibilities;
import com.atlassian.jira.bc.issue.worklog.WorklogInputParametersImpl;
import com.atlassian.jira.bc.issue.worklog.WorklogResult;
import com.atlassian.jira.bc.issue.worklog.WorklogService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONObject;
import com.opensymphony.workflow.InvalidInputException;
import com.sun.jersey.api.client.ClientResponse;

public abstract class Absence {

	private static final Logger log = LoggerFactory.getLogger(Absence.class);

	private Date startDate;
	private Date endDate;
	private Double numberOfWorkingDays;
	private ApplicationUser user;
	private TimeSpan timespan;
	private Issue issue;
	private IssueInputParameters issueInputParameters;
	private static final SimpleDateFormat df = new SimpleDateFormat("dd.MM.yy");
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private CustomField cfDays;
	private AbsenceHistory<? extends Absence> history;

	public static Absence newInstance(Issue issue) throws JiraValidationException {
		if(ConfigMap.get("issuetype.sickness").equals(issue.getIssueType().getName())) {
			return new Sickness(issue);
		} else {
			return new Vacation(issue);
		}
	}
	
	public Absence(Issue issue) throws JiraValidationException {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();

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
		timespan = new TimeSpan(user, startDate, endDate);
		log.debug("Number of working days: " + timespan.getNumberOfWorkingDays());
		Double days = Double.valueOf(timespan.getNumberOfWorkingDays());
		this.numberOfWorkingDays = (isHalfDay() ? 0.5 : 1.0) * days;
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

	protected abstract AbsenceHistory<? extends Absence> initHistory() throws JiraValidationException;

	public abstract boolean isHalfDay();

	
	private Long getOriginalEstimate() throws JiraValidationException {
		long result = 0;
		if (timespan == null) {
			timespan = new TimeSpan(user, startDate, endDate);
		}
		JSONArray allDays = timespan.getWorkingDays();
		int length = allDays.length();
		for (int i = 0; i < length; i++) {
			try {
				JSONObject day = allDays.getJSONObject(i);
				if ("WORKING_DAY".equals(day.get("type"))) {
					result  += day.getInt("requiredSeconds");
				}
			} catch (Exception e) {
				log.error("Error setting worklog", e);
			}
		}
		return isHalfDay() ? result/2 : result;
	}
	
	public void updateFieldValues() throws JiraValidationException {
		long seconds = getOriginalEstimate();
		String inMinutes = String.valueOf(seconds/60) + "m";
		log.debug("Setting original estimate to {}", inMinutes);
		issueInputParameters.setOriginalAndRemainingEstimate(inMinutes, inMinutes);
		issueInputParameters.addCustomFieldValue(cfDays.getId(), String.valueOf(numberOfWorkingDays));
	}

	public void updateIssue() {
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
	}

	public double getVacationDaysOfThisYear() throws JiraValidationException {
		return getHistory().getNumberOfPreviousHolidays();
	}

	public abstract void validate() throws InvalidInputException, JiraValidationException;

	public void setPlanitems() throws PlanItemException {
		PlanItemManager manager = new PlanItemManager(issue, isHalfDay() ? 50 : 100);
		manager.setTimespan(dateFormat.format(startDate), dateFormat.format(endDate));
		ClientResponse response = manager.createPlanItem();
		int status = response.getStatus();
		if (!(status == 200 || status == 201)) {
			throw new PlanItemException("Unable to set plan items, status code was: " + status + " response");
		}
		log.info("Successfully created plan item for issue {}", issue);
	}
	
	public void deletePlanitems() throws PlanItemException, JiraValidationException {
		PlanItemManager manager = new PlanItemManager(issue, isHalfDay() ? 50 : 100);
		manager.setTimespan(dateFormat.format(startDate), dateFormat.format(endDate));
		ClientResponse response = manager.deletePlanItems();
		int status = response.getStatus();
		if (!(status == 200 || status == 201)) {
			throw new PlanItemException("Unable to delete plan items, status code was: " + status + " response");
		}
		log.info("Successfully deleted plan item for issue {}", issue);
	}

	public abstract void writeVelocityComment(boolean finalApproval) throws JiraValidationException;
	
	public void setWorkLog() throws JiraValidationException {
		
		JiraServiceContext jiraServiceContext = new JiraServiceContextImpl(user);
		WorklogService worklogService = ComponentAccessor.getComponent(WorklogService.class);
		if (timespan == null) {
			timespan = new TimeSpan(user, startDate, endDate);
		}
		JSONArray allDays = timespan.getWorkingDays();
		int length = allDays.length();
		for (int i = 0; i < length; i++) {
			try {
				JSONObject day = allDays.getJSONObject(i);
				if ("WORKING_DAY".equals(day.get("type"))) {
					int seconds = day.getInt("requiredSeconds");
					if (isHalfDay()) {
						seconds = seconds / 2;
					}
					String timeSpent = String.valueOf(seconds/60) + "m";					
					Date date = dateFormat.parse(day.getString("date"));
					final WorklogInputParametersImpl.Builder builder = WorklogInputParametersImpl.issue(issue)
							.timeSpent(timeSpent)
							.startDate(date)
							.comment("Automatically created from MTC Absence plugin")
							.visibility(Visibilities.publicVisibility());
					WorklogResult result = worklogService.validateCreate(jiraServiceContext, builder.build());
					Worklog worklog = worklogService.createAndAutoAdjustRemainingEstimate(jiraServiceContext, result, true);
					log.debug("Created worklog {}, {} on issue {}", worklog.getStartDate(), timeSpent, issue);
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
		for(Worklog worklog : worklogs) {
			log.debug("Deleting worklog for issue", worklog.getStartDate(), worklog.getTimeSpent());
			WorklogResult worklogResult = worklogService.validateDelete(jiraServiceContext, worklog.getId());
			worklogService.deleteAndAutoAdjustRemainingEstimate(jiraServiceContext, worklogResult, true);
		}
	}

	public ApplicationUser getSupervisor() throws JiraValidationException {
		PropertyHelper props = new PropertyHelper(issue.getReporter());
		String supervisorName = props.exists(SUPERVISOR_KEY) ? String.valueOf(props.get(SUPERVISOR_KEY))
				: "teamlead";
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
		issueInputParameters.setAssigneeId(supervisor.getName());
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
	
	private void addVelocityContextParams(Class<?> clazz, Map<String,Object> map) {
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
