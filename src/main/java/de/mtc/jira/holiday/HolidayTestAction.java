package de.mtc.jira.holiday;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.web.action.JiraWebActionSupport;

public class HolidayTestAction extends JiraWebActionSupport {

	private final static Logger log = LoggerFactory.getLogger(HolidayTestAction.class);
	
	private static final long serialVersionUID = 1L;
	private String issue;
	private String result;
	private String start;
	private String end;
	private List<CustomFieldType<?,?>> customFieldTypes;

	public void setIssue(String issue) {
		this.issue = issue;
	}
	
	public List<CustomFieldType<?, ?>> getCustomFieldTypes() {
		return customFieldTypes;
	}

	public void setStart(String start) {
		this.start = start;
	}

	public void setEnd(String end) {
		this.end = end;
	}
	
	public String getResult() {
		return result;
	}

	@Override
	protected void doValidation() {
		super.doValidation();
	}

	@Override
	protected String doExecute() throws Exception {
		/*
		log.debug("Entering the execute method");
		Issue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(this.issue);
		PlanItemManager manager = new PlanItemManager(issue);
		manager.setStart(this.start);
		manager.setFinish(this.end);
		ClientResponse response = manager.getPlanningItems();
		this.result = response.getEntity(String.class);
		*/
		
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		List<CustomFieldType<?,?>> fieldTypes = cfm.getCustomFieldTypes();
		this.customFieldTypes = fieldTypes;
		return SUCCESS;
	}

	@Override
	public String doDefault() throws Exception {
		log.debug("Entering the default method");
		return INPUT;
	}
}
