package de.mtc.jira.holiday.workflow;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;

import de.mtc.jira.holiday.AbsenceUtil;

public class AssignToTkPostFunction extends AbstractJiraFunctionProvider {
	
	
	private static final Logger log = LoggerFactory.getLogger(AssignToTkPostFunction.class);

	@SuppressWarnings({ "rawtypes", "deprecation" })
	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {
		MutableIssue issue = getIssue(transientVars);
		ApplicationUser supervisor = AbsenceUtil.getSupervisor(issue.getReporter());
		issue.setAssignee(supervisor);
		issue.store();
		log.debug("Setting assignee to {}", issue.getAssignee().getName());
	}
}
