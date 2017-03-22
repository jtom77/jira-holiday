package de.mtc.jira.absence;

import com.opensymphony.workflow.WorkflowException;

public class JiraValidationException extends WorkflowException {

	private static final long serialVersionUID = 1L;

	public JiraValidationException(String message) {
		super(message);
	}
	
	public JiraValidationException(String message, Throwable e) {
		super(message, e);
	}
}
