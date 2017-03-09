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
import com.atlassian.jira.issue.context.manager.JiraContextTreeManager;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.CustomFieldUtils;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
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

		for (String propKey : new String[] { "cf.annual_leave", "cf.residual_days", "cf.working_days" }) {
			String name = ConfigMap.get(propKey);
			createCustomField(name, description, READ_ONLY);
		}

		String name = ConfigMap.get("cf.holiday_type");
		CustomField cf = createCustomField(name, description, SELECT);
		addOptions(cf);
	}

	private void addOptions(CustomField cf) {
		List<FieldConfigScheme> schemes = cf.getConfigurationSchemes();
		if (schemes == null) {
			return;
		}
		log.debug("Schemes for custom field {}: {}", cf.getName(), schemes);
		for (FieldConfigScheme sc : schemes) {
			@SuppressWarnings("rawtypes")
			Map configs = sc.getConfigsByConfig();
			if (configs != null && !configs.isEmpty()) {
				FieldConfig config = (FieldConfig) configs.keySet().iterator().next();
				OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
				List<String> existingOptions = optionsManager.getOptions(config).stream().map(t -> t.getValue())
						.collect(Collectors.toList());
				for(String value : Arrays.asList("Halbe Tage", "Ganze Tage")) {
					if(!existingOptions.contains(value)) {
						optionsManager.createOption(config, null, 1L, value);
						log.info("Added option {} to {}. All options {}", value, cf, optionsManager.getOptions(config));
					} else {
						log.info("Option {} already exists", value);
					}
				}
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

		log.info("Create custom field {} for projects {} and issue types {}", name, projectKey, issueTypes);

		Collection<CustomField> existing = cfm.getCustomFieldObjectsByName(name);
		if (existing != null && !existing.isEmpty()) {
			log.debug("Custom Field \"{}\" already exists", name);
			for (CustomField cf : existing) {
				refreshContext(cf, projectIds.toArray(new Long[projectIds.size()]), jiraContextNodes, issueTypes);
			}
			customFields.addAll(existing);
			return existing.iterator().next();
		}

		CustomField field = cfm.createCustomField(name, description, fieldType, null, jiraContextNodes, issueTypes);

		log.debug("Created Custom field. Name: {}, Id: {}, NameKey: {}, Class: {}", field.getName(), field.getId(),
				field.getNameKey(), field.getClass());
		customFields.add(field);
		// addToFieldScreen(field);
		return field;
	}

	public List<CustomField> getCustomFields() {
		return customFields;
	}

	public void refreshContext(CustomField cf, Long[] projectIds, List<JiraContextNode> contexts,
			List<IssueType> issueTypes) {

		log.debug("Refreshing context for field {}", cf);

		FieldConfigSchemeManager manager = ComponentAccessor.getFieldConfigSchemeManager();
		JiraContextTreeManager treeManager = ComponentAccessor.getComponent(JiraContextTreeManager.class);
		CustomFieldUtils.buildJiraIssueContexts(true, null, projectIds, treeManager);
		FieldConfigScheme newConfigScheme = new FieldConfigScheme.Builder().setName("TEST").setDescription("test")
				.setFieldId(cf.getId()).toFieldConfigScheme();
		manager.createFieldConfigScheme(newConfigScheme, contexts, issueTypes, cf);
		ComponentAccessor.getFieldManager().refresh();
		ComponentAccessor.getCustomFieldManager().refreshConfigurationSchemes(newConfigScheme.getId());
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
}
