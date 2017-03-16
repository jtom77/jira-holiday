package de.mtc.jira.holiday.webwork;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.ConfigurableJiraWorkflow;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowUtil;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;

public class WorkflowCreator {

	private final static Logger log = LoggerFactory.getLogger(WorkflowCreator.class);
	
	private static Map<String, String> workflowToScreenMap;
	static {
		workflowToScreenMap = new HashMap<>();
		workflowToScreenMap.put("BOHRS Holidays", "ISF Urlaub");
		workflowToScreenMap.put("BOHRS Sickleave", "ISF Krankmeldungen");
	}
	

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public JiraWorkflow createWorkflow(String workflowName, String xmlPath, Map<String, FieldScreen> fieldScreens,
			Map<String, Status> statuses) throws Exception {

		log.info("Start to create workflow for plugin jira-holiday");
		
		FieldScreen screen = fieldScreens.get(workflowToScreenMap.get(workflowName));

		log.debug("Working with screen: {}", screen.getName());

		InputStream in = this.getClass().getClassLoader().getResourceAsStream(xmlPath);
		String xml = null;

		log.debug("Parsing file {}", xmlPath);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
			xml = reader.lines().collect(Collectors.joining("\n"));
		}
		WorkflowDescriptor workflowDescriptor = null;
		workflowDescriptor = WorkflowUtil.convertXMLtoWorkflowDescriptor(xml);

		List<StepDescriptor> stepDescriptors = workflowDescriptor.getSteps();

		Map<String, String> actionNames = new HashMap<>();

		for (StepDescriptor stepDescriptor : stepDescriptors) {
			Status status = statuses.get(stepDescriptor.getName());
			if(status == null) {
				status = Configurator.createIfUndefinedAndGetStatus(stepDescriptor.getName(), "UNDEFINED");
			}

			Map attributes = stepDescriptor.getMetaAttributes();
			if (attributes == null) {
				attributes = new HashMap();
			}
			attributes.put("jira.status.id", status.getId());
			stepDescriptor.setMetaAttributes(attributes);

			log.debug("Status: {} Id: {}", status.getName(), status.getId());
			actionNames.put(status.getName(), status.getId());

			List<ActionDescriptor> actionDescriptors = stepDescriptor.getActions();
			for (ActionDescriptor actionDescriptor : actionDescriptors) {
				Map metaAttributes = actionDescriptor.getMetaAttributes();
				Object fieldScreenId = metaAttributes.get("jira.fieldscreen.id");
				if (!(fieldScreenId == null
						|| ((fieldScreenId instanceof String) && ((String) fieldScreenId).isEmpty()))) {
					if (getFieldScreenById(fieldScreenId) == null) {
						metaAttributes.put("jira.fieldscreen.id", screen.getId());
						log.debug("Replacing screen id {} with {} in action {}", fieldScreenId, screen.getId(),
								actionDescriptor.getName());
					}
				}
			}
		}

		WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
		JiraWorkflow workflow = new ConfigurableJiraWorkflow(workflowName, workflowDescriptor, workflowManager);
		ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
		workflowManager.createWorkflow(user, workflow);
		
		log.info("Workflow {} created!", workflow.getName());

		return workflow;

	}

	private static FieldScreen getFieldScreenById(Object o) {
		Long id = null;
		if (o instanceof Number) {
			id = ((Number) o).longValue();
		} else if (o instanceof String) {
			id = Long.parseLong(o.toString());
		} else {
			throw new IllegalArgumentException();
		}
		for (FieldScreen sc : ComponentAccessor.getFieldScreenManager().getFieldScreens()) {
			if (sc.getId() == id) {
				return sc;
			}
		}
		return null;
	}

}
