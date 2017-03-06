package de.mtc.jira.holiday.webwork;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.context.ProjectContext;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.web.action.JiraWebActionSupport;

import de.mtc.jira.holiday.ProjectCreator;
import de.mtc.jira.holiday.WorkflowHelper;

public class ProjectCreationWebWorker extends JiraWebActionSupport {

	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ProjectCreationWebWorker.class);
	
	public final static String TEXT_SINGLE_LINE = "com.atlassian.jira.plugin.system.customfieldtypes:textfield";
	public final static String DATE_PICKER = "com.atlassian.jira.plugin.system.customfieldtypes:datepicker";
	public final static String SELECT = "com.atlassian.jira.plugin.system.customfieldtypes:select";
	public final static String READ_ONLY = "com.atlassian.jira.plugin.system.customfieldtypes:readonlyfield";
	
	private List<CustomField> customFields;
	
	public List<CustomField> getCustomFields() {
		return customFields;
	}
			
	@Override
	protected String doExecute() throws Exception {
		log.debug("Executing main method");
		createAllFields();
		customFields = ComponentAccessor.getCustomFieldManager().getCustomFieldObjects();
		return SUCCESS;
	}
	
	@Override
	public String doDefault() throws Exception {
		log.debug("Executing default method");
		return INPUT;
	}
	
	public void createAllFields() {
		String description = "Automatically created for holiday MTC project";
		for(String propKey : new String[] {"cf.start_date","cf.end_date"}) {
			String name = WorkflowHelper.getProperty(propKey);
			createCustomField(name, description, DATE_PICKER);
		}
		for(String propKey : new String[] {"cf.annual_leave","cf.residual_days"}) {
			String name = WorkflowHelper.getProperty(propKey);
			createCustomField(name, description, READ_ONLY);
		}
	}
	
	
	public void createCustomField(String name, String description, String type) {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		CustomFieldType<?, ?> fieldType = cfm.getCustomFieldType(type);
		List<IssueType> issueTypes = new ArrayList<>(ComponentAccessor.getConstantsManager().getAllIssueTypeObjects());
		List<JiraContextNode> jiraContextNodes = ComponentAccessor.getProjectManager().getProjectObjects().stream()
				.map(project -> new ProjectContext(project.getId())).collect(Collectors.toList());
		try {
			Collection<CustomField> existing = cfm.getCustomFieldObjectsByName(name);
			if(existing != null && !existing.isEmpty()) {
				log.debug("Custom Field \"" + name + "\" already exists");
				return;
			}
			CustomField field = cfm.createCustomField(name, description,
					fieldType, null, jiraContextNodes, issueTypes);
			log.debug("## Created custom Field " + field.getName() + ", " + field.getId() + ", " + field.getNameKey() + " " + field.getClass());
			log.debug("Created Custom field. Name: %s, Id: %s, NameKey: %s, Class: %s", field.getName(), field.getId(), field.getNameKey(), field.getClass());
		} catch (Exception e) {
			log.debug(e.getMessage());
		}
	}
	
	public void deleteRedundantFields() {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		Map<String, CustomField> tempMap = new HashMap<>();
		for(CustomField cf : cfm.getCustomFieldObjects()) {
			String name = cf.getName();
			if(tempMap.get(name) == null) {
				tempMap.put(name, cf);
			} else {
				try {
					cfm.removeCustomField(cf);
				} catch (RemoveException e) {
					log.debug("Couldn't remove field " + cf.getName(), e);
				}
				log.debug("Successfully removed field " + cf.getName());
			}
		}
	}
}
