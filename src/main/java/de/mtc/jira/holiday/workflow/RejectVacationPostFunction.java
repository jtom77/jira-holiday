package de.mtc.jira.holiday.workflow;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;

import de.mtc.jira.holiday.Absence;

public class RejectVacationPostFunction extends AbstractJiraFunctionProvider {

	private static final Logger log = LoggerFactory.getLogger(RejectVacationPostFunction.class);
	
	@SuppressWarnings("rawtypes")
	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {
		Issue issue = getIssue(transientVars);
		try {
			Absence absence = Absence.newInstance(issue);
			absence.deletePlanitems();
			absence.deleteWorklogs();
			absence.updateFieldValues();
			absence.updateIssue();
		} catch(Exception e) {
			log.error(e.getMessage(), e);
		}	
	}	
}
