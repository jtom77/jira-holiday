package de.mtc.jira.holiday;

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
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;
import com.atlassian.jira.issue.worklog.WorklogImpl;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.jira.workflow.WorkflowException;
import com.atlassian.velocity.VelocityManager;
import com.opensymphony.module.propertyset.PropertySet;
import com.sun.jersey.api.client.ClientResponse;

public class WorkflowHelper {

	private static final Logger log = LoggerFactory.getLogger(WorkflowHelper.class);
	public static final String CF_START_DATE, CF_END_DATE, CF_YEARLY_VACATION, CF_REST_VACATION, CF_TYPE;
	private static final String ANNUAL_LEAVE, DAYS_OFF;
	@SuppressWarnings("unused")
	private static final String WHOLE_DAY, HALF_DAY;
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	private static final String SUPERVISOR_KEY;
	private static final String HR_MANAGER;

	private HistoryManager historyManager;
	
	private Vacation vacation;

	static {
		CF_START_DATE = ConfigMap.get("cf.start_date");
		CF_END_DATE = ConfigMap.get("cf.end_date");
		CF_YEARLY_VACATION = ConfigMap.get("cf.annual_leave");
		CF_REST_VACATION = ConfigMap.get("cf.residual_days");

		CF_TYPE = ConfigMap.get("cf.holiday_type");
		WHOLE_DAY = ConfigMap.get("cf.holiday_type.whole");
		HALF_DAY = ConfigMap.get("cf.holiday_type.half");

		ANNUAL_LEAVE = ConfigMap.get("prop.annual_leave");
		DAYS_OFF = ConfigMap.get("prop.days_off");

		SUPERVISOR_KEY = ConfigMap.get("prop.supervisor.key");
		HR_MANAGER = ConfigMap.get("prop.hr_manager");
	}

	private ApplicationUser user;
	private Issue issue;
	private PropertySet props;
	private IssueInputParameters issueInputParameters;
	private TimeSpan timespan;
	


	public WorkflowHelper(Issue issue) {
		this.issue = issue;
		this.user = issue.getReporter();
		this.props = ComponentAccessor.getUserPropertyManager().getPropertySet(user);
		this.issueInputParameters = ComponentAccessor.getIssueService().newIssueInputParameters();

		try {
			this.historyManager = new HistoryManager(user);
			historyManager.computeEntries();
		} catch (Exception e) {
			log.error("Unable to get History Manager for issue " + issue, e);
		}

		log.debug("WorflowHelper initialized, issue: {}, user: {}, props: {}", issue, user, props);
	}

	public CustomField getField(String name) throws WorkflowException {
		for (CustomField cf : ComponentAccessor.getCustomFieldManager().getCustomFieldObjects(issue)) {
			if (cf.getName().equals(name)) {
				return cf;
			}
		}
		throw new WorkflowException("Couldn't find custom field '" + name + "' for issue " + issue.getKey());
	}

	public void updateFieldValue(CustomField cf, String oldValue, String newValue) {
		cf.updateValue(null, issue, new ModifiedValue<Object>(oldValue, newValue), new DefaultIssueChangeHolder());
		issueInputParameters.addCustomFieldValue(cf.getId(), newValue);
	}

	public void setFieldValue(CustomField cf, String newValue) {
		((MutableIssue) issue).setCustomFieldValue(cf, newValue);
		issueInputParameters.addCustomFieldValue(cf.getId(), newValue);
	}

	public Double getAnnualLeave() {
		return props.getDouble(ANNUAL_LEAVE);
	}

	public double getNumberOfWorkingDays() throws JiraValidationException {
		double workingDays = getTimespan().getNumberOfWorkingDays();
		if (isHalfDay()) {
			workingDays = workingDays * 0.5;
		}
		return workingDays;
	}

	public void updateUserPropertiesFieldValues() throws JiraValidationException {
		double oldDaysOff = historyManager.getNumberOfPreviousHolidays();
		double daysOff = oldDaysOff + getNumberOfWorkingDays();
		String oldRestVacation = (String) issue.getCustomFieldValue(getField(CF_REST_VACATION));
		double restVacation = getAnnualLeave() - daysOff;
		// props.setString(DAYS_OFF, String.valueOf(daysOff));
		updateFieldValue(getField(CF_REST_VACATION), oldRestVacation, String.valueOf(restVacation));
	}

	public void setWorkLog() throws JiraValidationException {
		WorklogManager worklogManager = ComponentAccessor.getWorklogManager();
		JSONArray allDays = getTimespan().getWorkingDays();
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

	private boolean isHalfDay() {
		return HALF_DAY.equals(issue.getCustomFieldValue(getField(CF_TYPE)).toString());
	}

	public void deleteWorklogs() {
		ComponentAccessor.getWorklogManager().deleteWorklogsForIssue(issue);
	}

	public void initFieldValues() {
		setFieldValue(getField(CF_YEARLY_VACATION), String.valueOf(getAnnualLeave()));
	}

	public void setPlanitems() throws PlanItemException {
		PlanItemManager manager = new PlanItemManager(issue, isHalfDay() ? 50 : 100);
		manager.setTimespan(getFormattedStartDate(), getFormattedEndDate());
		ClientResponse response = manager.createPlanItem();
		int status = response.getStatus();
		if (!(status == 200 || status == 201)) {
			throw new PlanItemException("Unable to set plan items, status code was: " + status + " response");
		}
		log.info("Successfully created plan item for issue {}", issue);
	}

	public Date getStartDate() {
		return (Date) issue.getCustomFieldValue(getField(CF_START_DATE));
	}

	public Date getEndDate() {
		return (Date) issue.getCustomFieldValue(getField(CF_END_DATE));
	}

	public String getFormattedStartDate() {
		return dateFormat.format(getStartDate());
	}

	public String getFormattedEndDate() {
		return dateFormat.format(getEndDate());
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

	public void writeVelocityComment() throws JiraValidationException {
		VelocityManager manager = ComponentAccessor.getVelocityManager();
		Map<String, Object> contextParameters = new HashMap<>();
		contextParameters.put("vacations",
				historyManager.getVacations().stream().map(t -> t.getVelocityContextParams()).collect(Collectors.toList()));
		contextParameters.put("currentYear", historyManager.getCurrentYear());
		double previous = historyManager.getNumberOfPreviousHolidays();
		double wanted = getNumberOfWorkingDays();
		double rest = getAnnualLeave() - previous;
		double restAfter = rest - wanted;
		contextParameters.put("previous", previous);
		contextParameters.put("wanted", wanted);
		contextParameters.put("rest", rest);
		contextParameters.put("restAfter", restAfter);
		issueInputParameters.setComment(manager.getBody("templates/comment/", "comment.vm", contextParameters));
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

	public TimeSpan getTimespan() throws JiraValidationException {
		if (timespan == null) {
			timespan = new TimeSpan(user, getStartDate(), getEndDate());
		}
		return timespan;
	}

	public static void main(String[] args) {
		System.out.println(dateFormat.format(new Date()));
	}
}
