package de.mtc.jira.holiday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.sun.jersey.api.client.ClientResponse;

public class HolidayTestAction extends JiraWebActionSupport {

	private final static Logger log = LoggerFactory.getLogger(HolidayTestAction.class);
	
	
	private static final long serialVersionUID = 1L;
	private String issue;
	private String result;
	private String start;
	private String end;

	public void setIssue(String issue) {
		this.issue = issue;
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
		System.out.println("DO Validation");
		super.doValidation();
	}

	@Override
	protected String doExecute() throws Exception {
		System.out.println("Entering the execute method");
		Issue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(this.issue);
		System.out.println(issue);
		PlanItemManager manager = new PlanItemManager(issue);
		manager.setStart(this.start);
		manager.setFinish(this.end);
		ClientResponse response = manager.getPlanningItems();
		this.result = response.getEntity(String.class);
		System.out.println("RESULT: " + result);
		return SUCCESS;
	}

	@Override
	public String doDefault() throws Exception {
		System.out.println("Entering the default method");
		return "input";
	}
}
