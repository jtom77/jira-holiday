package de.mtc.jira.holiday;

import static de.mtc.jira.holiday.ConfigMap.CF_DAYS;
import static de.mtc.jira.holiday.ConfigMap.CF_END_DATE;
import static de.mtc.jira.holiday.ConfigMap.CF_START_DATE;
import static de.mtc.jira.holiday.ConfigMap.CF_TYPE;
import static de.mtc.jira.holiday.ConfigMap.HR_MANAGER;
import static de.mtc.jira.holiday.ConfigMap.PROP_ANNUAL_LEAVE;
import static de.mtc.jira.holiday.ConfigMap.SUPERVISOR_KEY;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.IssueService.IssueResult;
import com.atlassian.jira.bc.issue.IssueService.UpdateValidationResult;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.worklog.WorklogImpl;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.velocity.VelocityManager;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.InvalidInputException;
import com.sun.jersey.api.client.ClientResponse;

public class Vacation {

	private static final Logger log = LoggerFactory.getLogger(Vacation.class);

	private Date startDate;
	private Date endDate;
	private Double numberOfWorkingDays;
	private ApplicationUser user;
	private String type;
	boolean isHalfDay;
	private TimeSpan timespan;
	private Issue issue;
	private double annualLeave;
	private IssueInputParameters issueInputParameters;
	private static final SimpleDateFormat df = new SimpleDateFormat("dd.MM.yy");
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private CustomField cfDays;
	private CustomField cfAnnualLeave;
	private VacationHistory history;

	@SuppressWarnings("deprecation")
	public Vacation(Issue issue) throws JiraValidationException {

		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		this.user = issue.getReporter();
		this.issue = issue;

		CustomField cfStart = cfm.getCustomFieldObjectByName(CF_START_DATE);
		CustomField cfEnd = cfm.getCustomFieldObjectByName(CF_END_DATE);
		CustomField cfType = cfm.getCustomFieldObjectByName(CF_TYPE);
		if (cfStart == null || cfEnd == null || cfType == null) {
			throw new JiraValidationException("Field missing");
		}

		this.cfDays = cfm.getCustomFieldObjectByName(CF_DAYS);
		this.cfAnnualLeave = cfm.getCustomFieldObjectByName(PROP_ANNUAL_LEAVE);

		this.startDate = (Date) issue.getCustomFieldValue(cfStart);
		this.endDate = (Date) issue.getCustomFieldValue(cfEnd);
		this.type = (String) issue.getCustomFieldValue(cfType).toString();
		this.isHalfDay = type.contains("Halbe");
		this.issueInputParameters = ComponentAccessor.getIssueService().newIssueInputParameters();

		if (cfDays != null) {
			String s = (String) issue.getCustomFieldValue(cfDays);
			try {
				numberOfWorkingDays = Double.valueOf(s);
			} catch (Exception ex) {
				// we have a fallback
				updateNumberOfWorkingDaysFromTimeSpan();
			}
		} else {
			updateNumberOfWorkingDaysFromTimeSpan();
		}

		this.annualLeave = Double.valueOf(String
				.valueOf(ComponentAccessor.getUserPropertyManager().getPropertySet(user).getObject(PROP_ANNUAL_LEAVE)));
	}

	private final void updateNumberOfWorkingDaysFromTimeSpan() throws JiraValidationException {
		log.debug("Field {} was not set, getting time from tempo", cfDays.getName());
		timespan = new TimeSpan(user, startDate, endDate);
		log.debug("Number of working days: " + timespan.getNumberOfWorkingDays());
		numberOfWorkingDays = (isHalfDay ? 0.5 : 1.0) * Double.valueOf(timespan.getNumberOfWorkingDays());
		issueInputParameters.addCustomFieldValue(cfDays.getId(), String.valueOf(numberOfWorkingDays));
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

	public Double getNumberOfWorkingDays() {
		return numberOfWorkingDays;
	}

	public ApplicationUser getUser() {
		return user;
	}

	public String getType() {
		return type;
	}

	public boolean isHalfDay() {
		return isHalfDay;
	}

	public double getAnnualLeave() {
		return annualLeave;
	}

	public void updateFieldValues() {
		issueInputParameters.addCustomFieldValue(cfDays.getId(), String.valueOf(numberOfWorkingDays));
		issueInputParameters.addCustomFieldValue(cfAnnualLeave.getId(), String.valueOf(annualLeave));
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
		if (history == null) {
			history = VacationHistory.getHistory(this);
		}
		return history.getNumberOfPreviousHolidays();
	}

	public void validate() throws InvalidInputException, JiraValidationException {
		if (startDate.compareTo(endDate) > 0) {
			throw new InvalidInputException("End date must be before start date");
		}
		if (annualLeave - getVacationDaysOfThisYear() - getNumberOfWorkingDays() < 0) {
			throw new InvalidInputException("There are not enough vacation days left");
		}
	}

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

	public void writeVelocityComment(boolean finalApproval) throws JiraValidationException {
		VelocityManager manager = ComponentAccessor.getVelocityManager();
		Map<String, Object> contextParameters = new HashMap<>();
		contextParameters.put("vacations",
				history.getVacations().stream().map(t -> t.getVelocityContextParams()).collect(Collectors.toList()));
		contextParameters.put("vacation", getVelocityContextParams());
		contextParameters.put("currentYear", history.getCurrentYear());
		double previous = getVacationDaysOfThisYear();
		double wanted = getNumberOfWorkingDays();
		double rest = annualLeave - previous;
		double restAfter = rest - wanted;
		contextParameters.put("previous", previous);
		contextParameters.put("wanted", wanted);
		contextParameters.put("rest", rest);
		contextParameters.put("restAfter", restAfter);
		String template = finalApproval ? "comment_approved.vm" : "comment.vm";
		issueInputParameters.setComment(manager.getBody("templates/comment/", template, contextParameters));
	}

	public void setWorkLog() throws JiraValidationException {
		WorklogManager worklogManager = ComponentAccessor.getWorklogManager();
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
					Date date = dateFormat.parse(day.getString("date"));
					WorklogImpl worklog = new WorklogImpl(null, issue, 0L, user.getKey(), "Urlaub", date, null, null,
							Long.valueOf(seconds));
					worklogManager.create(user, worklog, 0L, false);
				}
			} catch (Exception e) {
				log.error("Error setting worklog", e);
			}
		}
	}

	public void deleteWorklogs() {
		ComponentAccessor.getWorklogManager().deleteWorklogsForIssue(issue);
	}

	public ApplicationUser getSupervisor() throws JiraValidationException {
		PropertySet props = ComponentAccessor.getUserPropertyManager().getPropertySet(issue.getReporter());
		String supervisorName = props.getString(SUPERVISOR_KEY);
		if (supervisorName == null || supervisorName.isEmpty()) {
			supervisorName = "teamlead";
			props.setString(SUPERVISOR_KEY, supervisorName);
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

	public Map<String, Object> getVelocityContextParams() {
		Class<Vacation> clazz = Vacation.class;
		Map<String, Object> result = new HashMap<>();
		for (Field field : clazz.getDeclaredFields()) {
			try {
				field.setAccessible(true);
				Object value = field.get(this);
				if (value instanceof Date) {
					value = df.format(value);
				}
				result.put(field.getName(), value);
			} catch (Exception e) {
				log.error("Couldn't create velocity context params", e);
			}
		}
		return result;
	}
}