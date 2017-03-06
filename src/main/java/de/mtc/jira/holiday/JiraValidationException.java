package de.mtc.jira.holiday;

import com.opensymphony.workflow.WorkflowException;

public class JiraValidationException extends WorkflowException {

	public JiraValidationException(String message) {
		super(message);
	}
	
	public JiraValidationException(String message, Throwable e) {
		super(message, e);
	}
}
