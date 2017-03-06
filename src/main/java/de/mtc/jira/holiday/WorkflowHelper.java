package de.mtc.jira.holiday;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.jira.workflow.WorkflowException;
import com.opensymphony.module.propertyset.PropertySet;
import com.sun.jersey.api.client.ClientResponse;

public class WorkflowHelper {

	private static final Logger log = LoggerFactory.getLogger(WorkflowHelper.class);
	private static final String CF_START_DATE, CF_END_DATE, CF_YEARLY_VACATION, CF_REST_VACATION;
	private static final String ANNUAL_LEAVE, DAYS_OFF;
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private static final Properties properties;

	static {

		properties = new Properties();
		try {
			properties.load(WorkflowHelper.class.getClassLoader().getResourceAsStream("jira-holiday.properties"));
		} catch (IOException e) {
			log.error("FATAL: Failed to load properties", e);
		}

		CF_START_DATE = getProperty("cf.start_date");
		CF_END_DATE = getProperty("cf.end_date");
		CF_YEARLY_VACATION = getProperty("cf.annual_leave");
		CF_REST_VACATION = getProperty("cf.residual_days");

		ANNUAL_LEAVE = getProperty("prop.annual_leave");
		DAYS_OFF = getProperty("prop.days_off");
	}

	private ApplicationUser user;
	private Issue issue;
	private PropertySet props;
	private IssueInputParameters issueInputParameters;
	private JSONObject timesheet;

	public WorkflowHelper(Issue issue) {
		this.issue = issue;
		this.user = issue.getReporter();
		this.props = ComponentAccessor.getUserPropertyManager().getPropertySet(user);
		this.issueInputParameters = ComponentAccessor.getIssueService().newIssueInputParameters();
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

	public void getFieldValue(Issue issue, CustomField cf) {
		issue.getCustomFieldValue(cf);
	}

	public Integer getAnnualLeave() {
		String annualLeave = props.getString(ANNUAL_LEAVE);
		if (annualLeave == null) {
			annualLeave = "30";
			props.setString(ANNUAL_LEAVE, annualLeave);
		}
		return Integer.parseInt(annualLeave);
	}

	public void setAnnualLeave(String annualLeave) {
		props.setString(ANNUAL_LEAVE, annualLeave);
	}

	public Integer getDaysOff() {
		String daysOff = props.getString(DAYS_OFF);
		if (daysOff == null) {
			daysOff = "0";
			props.setString(DAYS_OFF, daysOff);
		}
		return Integer.parseInt(daysOff);
	}

	public void setDaysOff(String daysOff) {
		props.setString(DAYS_OFF, daysOff);
	}

	public String getResidualLeave() {
		return String.valueOf(getAnnualLeave() - getDaysOff());
	}

	public void updateUserPropertiesFieldValues() throws Exception {
		Integer workingDays = getNumberOfWorkingDays();
		String oldDaysOff = props.getString(DAYS_OFF);
		int daysOff = Integer.parseInt(oldDaysOff) + workingDays;
		int restVacation = Integer.parseInt(props.getString(ANNUAL_LEAVE)) - daysOff;
		props.setString(DAYS_OFF, String.valueOf(daysOff));
		updateFieldValue(getField(CF_REST_VACATION), oldDaysOff, String.valueOf(restVacation));
	}

	public void setWorkLog() throws Exception {
		WorklogManager worklogManager = ComponentAccessor.getWorklogManager();
		JSONArray allDays = getWorkingDays();
		int length = allDays.length();
		for (int i = 0; i < length; i++) {
			try {
				JSONObject day = allDays.getJSONObject(i);
				if ("WORKING_DAY".equals(day.get("type"))) {
					int seconds = day.getInt("requiredSeconds");
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

	public void initFieldValues() {
		setFieldValue(getField(CF_YEARLY_VACATION), String.valueOf(getAnnualLeave()));
		setFieldValue(getField(CF_REST_VACATION), String.valueOf(getResidualLeave()));
	}

	public void setPlanitems() {
		PlanItemManager manager = new PlanItemManager(issue);
		manager.setTimespan(getStartDateAsString(), getEndDateAsString());
		manager.createPlanItem();
	}

	public Date getStartDate() {
		return (Date) issue.getCustomFieldValue(getField(CF_START_DATE));
	}

	public Date getEndDate() {
		return (Date) issue.getCustomFieldValue(getField(CF_END_DATE));
	}

	public String getStartDateAsString() {
		return dateFormat.format(getStartDate());
	}

	public String getEndDateAsString() {
		return dateFormat.format(getEndDate());
	}

	
	public ApplicationUser getSuperVisor() throws JiraValidationException {
		String supervisorName = "teamlead"; // props.getString("jira.meta.Tempo.mySupervisor");
		ApplicationUser supervisor = ComponentAccessor.getUserManager().getUserByName(supervisorName);
		if(supervisor == null) {
			throw new JiraValidationException("No supervisor defined for user: " + user);
		}
		return supervisor;
	}
	
	public void assignToSuperVisor() throws JiraValidationException {
		ApplicationUser supervisor = getSuperVisor();
		issueInputParameters.setAssigneeId(supervisor.getName());
		log.info("Assignee for issue {} is set to: {}", issue.getKey(), supervisor.getName());
	}

	public ApplicationUser getHumanResourcesManager() throws JiraValidationException {
		String supervisorName = "manager"; // props.getString("jira.meta.Tempo.mySupervisor");
		ApplicationUser supervisor = ComponentAccessor.getUserManager().getUserByName(supervisorName);
		if(supervisor == null) {
			throw new JiraValidationException("No hr manager defined for user: " + user);
		}
		return supervisor;
	}
	
	public void assignToHumanResourceManager() throws JiraValidationException {
		ApplicationUser manager = getHumanResourcesManager();
		issueInputParameters.setAssigneeId(manager.getName());
		log.info("Assignee for issue {} is set to: {}", issue.getKey(), manager.getName());
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

	private JSONObject getTimeSheet() throws JiraValidationException {
		if (timesheet == null) {
			Map<String, String> replacements = new HashMap<>();
			replacements.put("user", user.getName());
			replacements.put("start", getStartDateAsString());
			replacements.put("end", getEndDateAsString());
			String req = getProperty("rest.api.workingdays", replacements);
			JiraRestClient client = new JiraRestClient();
			ClientResponse response = client.get(req);
			if (!(response.getStatus() == ClientResponse.Status.OK.getStatusCode())) {
				throw new JiraValidationException("Request " + req + " failed: " + response);
			}
			String json = response.getEntity(String.class);
			try {
				timesheet = new JSONObject(json);
			} catch (Exception e) {
				throw new JiraValidationException(
						"Unable to parse response for request " + req + ".Server response: " + json, e);
			}
		}
		return timesheet;
	}

	public int getNumberOfWorkingDays() throws JiraValidationException {
		try {
			return getTimeSheet().getInt("numberOfWorkingDays");
		} catch (Exception e) {
			throw new JiraValidationException("Unexpected JSON format: ", e);
		}
	}

	public JSONArray getWorkingDays() throws JiraValidationException {
		try {
			return getTimeSheet().getJSONArray("days");
		} catch (JSONException e) {
			throw new JiraValidationException("Unexpected JSON format: ", e);
		}
	}

	public static String getProperty(String key, Map<String, String> replacements) {
		String value = properties.getProperty(key);
		if (replacements != null) {
			value = processTemplate(value, replacements);
		}
		return value;
	}

	public static String processTemplate(String template, Map<String, String> replacements) {
		String result = template;
		for (String key : replacements.keySet()) {
			result = result.replace("{" + key + "}", replacements.get(key));
		}
		return result;
	}

	public static String getProperty(String key) {
		return getProperty(key, null);
	}

	public static void main(String[] args) {
		System.out.println(dateFormat.format(new Date()));
	}
}
