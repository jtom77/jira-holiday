package de.mtc.jira.holiday.webwork;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.StatusManager;
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
	private final static String XML = "workflow.xml";
	private final static String WORKFLOW_NAME = "WFX";

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void createWorkflow(Configurator configurator) throws Exception {

		log.info("Start to create workflow for plugin jira-holiday");

		FieldScreen screen = null; 
		for(Map.Entry<String, FieldScreen> entry : configurator.getAvailableScreens().entrySet()) {
			// TODO we need two workflows
			if(entry.getKey().contains("Urlaub")) {
				screen = entry.getValue();
				break;
			}
		}

		log.debug("Working with screen: {}", screen.getName());

		InputStream in = this.getClass().getClassLoader().getResourceAsStream(XML);
		String xml = null;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
			xml = reader.lines().collect(Collectors.joining("\n"));
		}
		WorkflowDescriptor workflowDescriptor = null;
		workflowDescriptor = WorkflowUtil.convertXMLtoWorkflowDescriptor(xml);

		StatusManager statusManager = ComponentAccessor.getComponent(StatusManager.class);
		List<StepDescriptor> stepDescriptors = workflowDescriptor.getSteps();
		Collection<Status> existingStatuses = statusManager.getStatuses();

		Map<String, String> actionNames = new HashMap<>();

		for (StepDescriptor stepDescriptor : stepDescriptors) {

			Status status = null;
			inner: for (Status existingStatus : existingStatuses) {
				if (existingStatus.getName().equals(stepDescriptor.getName())) {
					status = existingStatus;
					break inner;
				}
			}
			if (status == null) {
				status = statusManager.createStatus(stepDescriptor.getName(), stepDescriptor.getName(), "/images/icons/pluginIcon.png");
			}

			Map attributes = stepDescriptor.getMetaAttributes();
			if(attributes == null) {
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

		// String exportedXML = WorkflowUtil.convertDescriptorToXML(workflowDescriptor);
		// log.info(exportedXML);
		
		WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
		JiraWorkflow workflow = new ConfigurableJiraWorkflow(WORKFLOW_NAME, workflowDescriptor, workflowManager);
		ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
		workflowManager.createWorkflow(user, workflow);
		
		log.info("Workflow {} created!", workflow.getName());
			
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
