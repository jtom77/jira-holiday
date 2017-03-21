package de.mtc.jira.holiday.workflow;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;

import de.mtc.jira.holiday.Absence;

public class CreateIssuePostFunction extends AbstractJiraFunctionProvider {
	
	private static Logger log = LoggerFactory.getLogger(CreateIssuePostFunction.class);

	@SuppressWarnings({ "rawtypes" })
	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {		
		MutableIssue issue = getIssue(transientVars);
		log.debug("Executing create post function on issue " + issue);
		Absence absence = Absence.newInstance(issue);
		absence.updateFieldValues();
		absence.writeVelocityComment(false);
		absence.updateIssue();
	}
}
