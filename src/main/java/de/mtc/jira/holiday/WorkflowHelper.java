package de.mtc.jira.holiday;

import java.text.SimpleDateFormat;
import java.util.Date;

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
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.jira.workflow.WorkflowException;
import com.opensymphony.module.propertyset.PropertySet;
import com.sun.jersey.api.client.ClientResponse;

public class WorkflowHelper {

	private final static Logger log = LoggerFactory.getLogger(WorkflowHelper.class);

	private final static String CF_START_DATE = "Start Date";
	private final static String CF_END_DATE = "End Date";
	private final static String CF_YEARLY_VACATION = "Jahresurlaub";
	private final static String CF_REST_VACATION = "Resturlaub";

	private final static String ANNUAL_LEAVE = "jira.meta.annualLeave";
	private final static String DAYS_OFF = "jira.meta.daysOf";

	private ApplicationUser user;
	private Issue issue;
	private PropertySet props;
	private IssueInputParameters issueInputParameters;
	
	private final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd");

	public WorkflowHelper(Issue issue) {
		this.issue = issue;
		this.user = issue.getReporter();
		this.props = ComponentAccessor.getUserPropertyManager().getPropertySet(user);
		this.issueInputParameters = ComponentAccessor.getIssueService().newIssueInputParameters();
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

	public void initFieldValues() {
		setFieldValue(getField(CF_YEARLY_VACATION), String.valueOf(getAnnualLeave()));
		setFieldValue(getField(CF_REST_VACATION), String.valueOf(getResidualLeave()));
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
	
	public void assignToSuperVisor() {
		String supervisorName = "teamlead"; // props.getString("jira.meta.Tempo.mySupervisor");
		ApplicationUser supervisor = ComponentAccessor.getUserManager().getUserByName(supervisorName);
		if (supervisor == null) {
			log.error("Couldn't find supervisor: " + supervisorName);
		} else {
			issueInputParameters.setAssigneeId(supervisor.getName());
			log.info("Assignee is set to: " + supervisor);
		}
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
	
	
	public Integer getNumberOfWorkingDays() {
		String req = "rest/tempo-core/1/user/schedule/?user=" + user.getName() + "&from=" + getStartDateAsString() + "&to=" + getEndDateAsString(); 
		JiraRestClient client = new JiraRestClient();
		ClientResponse response = client.get(req);
		try {
			JSONObject json = new JSONObject(response.getEntity(String.class));
			return Integer.parseInt(json.get("numberOfWorkingDays").toString());
		} catch(Exception ex) {
			log.error(ex.getMessage());
		}
		return 10;
	}
	
}
