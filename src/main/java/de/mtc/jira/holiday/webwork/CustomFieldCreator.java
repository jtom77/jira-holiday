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
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;

import de.mtc.jira.holiday.ConfigMap;

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
			String name = ConfigMap.get(propKey);
			createCustomField(name, description, DATE_PICKER);
		}

		for (String propKey : new String[] { "cf.annual_leave", "cf.residual_days", "cf.days" }) {
			String name = ConfigMap.get(propKey);
			createCustomField(name, description, READ_ONLY);
		}

		String name = ConfigMap.get("cf.holiday_type");
		CustomField cf = createCustomField(name, description, SELECT);
		setOptions(cf);
	}

	private void setOptions(CustomField cf) {
		FieldConfigSchemeManager fieldConfigSchemeManager = ComponentAccessor.getFieldConfigSchemeManager();
		OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
		List<FieldConfigScheme> schemes = fieldConfigSchemeManager.getConfigSchemesForField(cf);
		for (FieldConfigScheme scheme : schemes) {
			FieldConfig config = scheme.getOneAndOnlyConfig();
			List<Option> existingOptions = optionsManager.getOptions(config);
			List<String> optionsToAdd = Arrays.asList("Halbe Tage", "Ganze Tage");
			for (Option option : existingOptions) {
				String existingValue = option.getValue();
				if (optionsToAdd.contains(existingValue)) {
					log.debug("Field {} already contains option {}", cf.getName(), existingValue);
					optionsToAdd.remove(existingValue);
				}
			}
			Long sequence = 1L;
			for (String value : optionsToAdd) {
				log.debug("Adding option {}.", value);
				Option option = optionsManager.createOption(config, null, sequence++, value);
				existingOptions.add(option);
				optionsManager.updateOptions(existingOptions);
			}
		}
	}

	private CustomField createCustomField(String name, String description, String type) throws GenericEntityException {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		CustomFieldType<?, ?> fieldType = cfm.getCustomFieldType(type);

		String relevantIssueTypes = ConfigMap.get("holiday.issuetypes");
		String projectKey = ConfigMap.get("holiday.project.key");

		List<IssueType> issueTypes = new ArrayList<>(ComponentAccessor.getConstantsManager().getAllIssueTypeObjects());

		if (!relevantIssueTypes.isEmpty()) {
			List<String> types = Arrays.asList(relevantIssueTypes.split(","));
			issueTypes = issueTypes.stream().filter(t -> types.contains(t.getName())).collect(Collectors.toList());
		}

		List<Long> projectIds = ComponentAccessor.getProjectManager().getProjectObjects().stream()
				.filter(t -> t.getKey().equals(projectKey)).map(t -> t.getId()).collect(Collectors.toList());

		List<JiraContextNode> jiraContextNodes = projectIds.stream().map(id -> new ProjectContext(id))
				.collect(Collectors.toList());

		CustomField field = null;
		log.info("Trying to create custom field {} for projects {} and issue types {}", name, projectKey, issueTypes);
		Collection<CustomField> existing = cfm.getCustomFieldObjectsByName(name);
		if (existing != null && !existing.isEmpty()) {
			log.debug("Custom Field \"{}\" already exists", name);
			customFields.addAll(existing);
			field = existing.iterator().next();
		} else {
			field = cfm.createCustomField(name, description, fieldType, null, jiraContextNodes, issueTypes);
			log.debug("Created Custom field. Name: {}, Id: {}, NameKey: {}, Class: {}", field.getName(), field.getId(),
					field.getNameKey(), field.getClass());
			customFields.add(field);
		}
		return field;
	}

	public List<CustomField> getCustomFields() {
		return customFields;
	}

	public void refreshContext(CustomField cf, Long[] projectIds, List<JiraContextNode> contexts,
			List<IssueType> issueTypes) {
		log.debug("Refreshing context for field {}", cf);
		FieldConfigSchemeManager fieldConfigSchemeManager = ComponentAccessor.getFieldConfigSchemeManager();
		for(FieldConfigScheme fieldConfigScheme : fieldConfigSchemeManager.getConfigSchemesForField(cf)) {
			fieldConfigSchemeManager.updateFieldConfigScheme(fieldConfigScheme, contexts, cf);
			ComponentAccessor.getFieldManager().refresh();
			ComponentAccessor.getCustomFieldManager().refreshConfigurationSchemes(fieldConfigScheme.getId());
		}
	}
}
