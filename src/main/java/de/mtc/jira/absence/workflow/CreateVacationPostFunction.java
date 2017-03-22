package de.mtc.jira.absence.workflow;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;

import de.mtc.jira.absence.Absence;

public class CreateVacationPostFunction extends AbstractJiraFunctionProvider {

	private final static Logger log = LoggerFactory.getLogger(CreateVacationPostFunction.class);

	@SuppressWarnings("rawtypes")
	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {
		Issue issue = getIssue(transientVars);
		log.debug("Executing post function on issue " + issue.getKey());
		Absence absence = Absence.newInstance(issue);
		try {
			absence.setPlanitems();
			absence.setWorkLog();
			absence.updateFieldValues();
			absence.writeVelocityComment(true);
			absence.updateIssue();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}
}
