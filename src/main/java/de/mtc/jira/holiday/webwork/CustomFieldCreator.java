package de.mtc.jira.holiday.webwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.context.ProjectContext;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.issuetype.IssueType;

import de.mtc.jira.holiday.WorkflowHelper;

public class CustomFieldCreator {
	
	private static final Logger log = LoggerFactory.getLogger(CustomFieldCreator.class);

	public final static String TEXT_SINGLE_LINE = "com.atlassian.jira.plugin.system.customfieldtypes:textfield";
	public final static String DATE_PICKER = "com.atlassian.jira.plugin.system.customfieldtypes:datepicker";
	public final static String SELECT = "com.atlassian.jira.plugin.system.customfieldtypes:select";
	public final static String READ_ONLY = "com.atlassian.jira.plugin.system.customfieldtypes:readonlyfield";

	private List<CustomField> customFields;
	
	public void createAllFields() throws GenericEntityException {

		customFields = new ArrayList<>();
		
		String description = "Automatically created for holiday MTC project";
		for (String propKey : new String[] { "cf.start_date", "cf.end_date" }) {
			String name = WorkflowHelper.getProperty(propKey);
			createCustomField(name, description, DATE_PICKER);
		}

		for (String propKey : new String[] { "cf.annual_leave", "cf.residual_days" }) {
			String name = WorkflowHelper.getProperty(propKey);
			createCustomField(name, description, READ_ONLY);
		}

		String name = WorkflowHelper.getProperty("cf.holiday_type");
		CustomField cf = createCustomField(name, description, SELECT);
		if (cf != null) {
			List<FieldConfigScheme> schemes = cf.getConfigurationSchemes();
			if (schemes != null && !schemes.isEmpty()) {
				FieldConfigScheme sc = schemes.get(0);
				@SuppressWarnings("rawtypes")
				Map configs = sc.getConfigsByConfig();
				if (configs != null && !configs.isEmpty()) {
					FieldConfig config = (FieldConfig) configs.keySet().iterator().next();
					OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
					List<Option> options = optionsManager.createOptions(config, null, 1L,
							Arrays.asList("Halbe Tage", "Ganze Tage"));
					log.info("Added Options {} to {}. All options {}", options, cf, optionsManager.getOptions(config));
				}
			}
		}
	}

	private CustomField createCustomField(String name, String description, String type) throws GenericEntityException {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		CustomFieldType<?, ?> fieldType = cfm.getCustomFieldType(type);
		List<IssueType> issueTypes = new ArrayList<>(ComponentAccessor.getConstantsManager().getAllIssueTypeObjects());
		List<JiraContextNode> jiraContextNodes = ComponentAccessor.getProjectManager().getProjectObjects().stream()
				.map(project -> new ProjectContext(project.getId())).collect(Collectors.toList());

		Collection<CustomField> existing = cfm.getCustomFieldObjectsByName(name);
		if (existing != null && !existing.isEmpty()) {
			customFields.addAll(existing);
			log.debug("Custom Field \"" + name + "\" already exists");
			return null;
		}
		CustomField field = cfm.createCustomField(name, description, fieldType, null, jiraContextNodes, issueTypes);
		log.debug("## Created custom Field " + field.getName() + ", " + field.getId() + ", " + field.getNameKey() + " "
				+ field.getClass());
		log.debug("Created Custom field. Name: %s, Id: %s, NameKey: %s, Class: %s", field.getName(), field.getId(),
				field.getNameKey(), field.getClass());
		customFields.add(field);
		//addToFieldScreen(field);
		return field;
	}

	public List<CustomField> getCustomFields() {
		return customFields;
	}
	
	public void deleteRedundantFields() {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		Map<String, CustomField> tempMap = new HashMap<>();
		for (CustomField cf : cfm.getCustomFieldObjects()) {
			String name = cf.getName();
			if (tempMap.get(name) == null) {
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

//	private void addToFieldScreen(CustomField cf) {
//		FieldScreenManager fieldScreenManager = ComponentAccessor.getFieldScreenManager();
//		
//		for (FieldScreen screen : fieldScreenManager.getFieldScreens()) {
//			if (screen.getName().startsWith(WorkflowHelper.getProperty("holiday.project.key"))) {
//				System.out.println(screen.getName());
//				System.out.println(screen.getDescription());
//				
//				log.info("Adding Customfield {} to screen {}", cf.getName(), screen.getName());
//				screen.getTab(0).addFieldScreenLayoutItem(cf.getId());
//			}
//		}
//	}
}
