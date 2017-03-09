package de.mtc.jira.holiday.workflow;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;

import de.mtc.jira.holiday.WorkflowHelper;

public class CreateVacationPostFunction extends AbstractJiraFunctionProvider {

	private final static Logger log = LoggerFactory.getLogger(CreateVacationPostFunction.class);

	@SuppressWarnings("rawtypes")
	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {
		Issue issue = getIssue(transientVars);
		log.debug("Executing post function on issue " + issue.getKey());
		WorkflowHelper wf = new WorkflowHelper(issue);
		try {
			wf.updateUserPropertiesFieldValues();
			wf.setWorkLog();
			wf.setPlanitems();
			wf.writeVelocityComment(true);
			wf.updateIssue();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}
}
