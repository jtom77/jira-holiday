package de.mtc.jira.absence.workflow;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;

import de.mtc.jira.absence.Absence;

public class CreateSicknessIssuePostFunction extends AbstractJiraFunctionProvider {
	
	private static Logger log = LoggerFactory.getLogger(CreateIssuePostFunction.class);

	@SuppressWarnings({ "rawtypes", "deprecation" })
	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {		
		MutableIssue issue = getIssue(transientVars);
		log.debug("Executing create post function on issue " + issue);
		Absence absence = Absence.newInstance(issue);
		absence.updateFieldValues();
		absence.writeVelocityComment(false);
		absence.updateIssue();
//		log.debug("Orignal estimate is set to " + issue.getOriginalEstimate());
//
//		issue.store();
	}
}

