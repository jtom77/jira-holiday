package de.mtc.jira.holiday.webwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.context.ProjectContext;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.OrderableField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenImpl;
import com.atlassian.jira.issue.fields.screen.FieldScreenLayoutItem;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreenSchemeItem;
import com.atlassian.jira.issue.fields.screen.FieldScreenSchemeItemImpl;
import com.atlassian.jira.issue.fields.screen.FieldScreenSchemeManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenTab;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeEntity;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeEntityImpl;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeImpl;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.operation.IssueOperations;
import com.atlassian.jira.issue.operation.ScreenableIssueOperation;
import com.atlassian.jira.project.Project;

import de.mtc.jira.holiday.ConfigMap;

public class Configurator {

	private final static Logger log = LoggerFactory.getLogger(Configurator.class);

	private final FieldScreenManager fieldScreenManager;
	private final FieldScreenSchemeManager fieldScreenSchemeManager;
	private final FieldManager fieldManager;
	private final IssueTypeScreenSchemeManager issueTypeScreenSchemeManager;
	private final ConstantsManager constantsManager;
	private final CustomFieldManager cfm;

	private final Map<String, CustomField> customFields = new HashMap<>();
	private final Map<String, FieldScreen> fieldScreens = new HashMap<>();
	private final Map<String, FieldScreenScheme> fieldScreenSchemes = new HashMap<>();

	private List<IssueType> allIssueTypes = new ArrayList<>();
	private final Document doc;

	/**
	 * 
	 * @throws Exception
	 */
	public Configurator() throws Exception {

		fieldScreenManager = ComponentAccessor.getFieldScreenManager();
		fieldScreenSchemeManager = ComponentAccessor.getComponent(FieldScreenSchemeManager.class);
		fieldManager = ComponentAccessor.getFieldManager();
		issueTypeScreenSchemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager();
		constantsManager = ComponentAccessor.getConstantsManager();
		cfm = ComponentAccessor.getCustomFieldManager();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		doc = db.parse(Configurator.class.getClassLoader().getResourceAsStream("fielddescription.xml"));

		// Create issue types
		log.debug("\n\n ISSUE TYPES \n\n");
		NodeList nl = doc.getElementsByTagName("issuetype");
		for (int i = 0; i < nl.getLength(); i++) {
			Element el = (Element) nl.item(i);
			String name = el.getAttribute("name");
			allIssueTypes.add(createIfNecesseryAndGetIssueType(name));
		}

		// Create custom fields
		log.debug("\n\n CUSTOM FIELDS \n\n");
		nl = doc.getElementsByTagName("customfield");
		for (int i = 0; i < nl.getLength(); i++) {
			Element el = (Element) nl.item(i);
			String name = el.getAttribute("name");
			String type = el.getAttribute("type");
			CustomField cf = getCustomField(name);
			if (cf == null) {
				String description = "Automatically created";
				cf = createIfNeccessaryAndGetCustomField(name, description, type, allIssueTypes);
			}
			List<String> options = new ArrayList<>();
			NodeList optionElements = el.getElementsByTagName("option");
			for (int j = 0; j < optionElements.getLength(); j++) {
				options.add(((Element) optionElements.item(j)).getAttribute("name"));
			}
			log.debug("Setting options {}", options);
			setOptionsIfNecessary(cf, options);
			log.debug("Initialised custom field {} [{}]", cf.getName(), cf.getId());
			customFields.put(cf.getName(), cf);
		}

		// Create field screens and add the custom fields
		log.debug("\n\n FIELD SCREENS \n\n");
		nl = doc.getElementsByTagName("fieldscreen");
		for (int i = 0; i < nl.getLength(); i++) {
			Element el = (Element) nl.item(i);
			FieldScreen fieldScreen = createIfNecessaryAndGetFieldScreen(el.getAttribute("name"));
			NodeList children = el.getElementsByTagName("screenitem");
			log.debug("Initialising field screen {}", fieldScreen.getName());
			for (int j = 0; j < children.getLength(); j++) {
				Element fieldElement = (Element) children.item(j);
				String name = fieldElement.getAttribute("name");
				String type = fieldElement.getAttribute("type");
				log.debug("Adding field {} to screen {}", name, fieldScreen.getName());
				if ("field".equals(type)) {
					addFieldToScreen(fieldScreen, name);
				} else {
					addCustomFieldToScreen(fieldScreen.getTab(0), customFields.get(name));
				}
			}
			fieldScreens.put(fieldScreen.getName(), fieldScreen);
		}

		// Create field screen schemes
		log.debug("\n\n FIELD SCREEN SCHEMES \n\n");
		nl = doc.getElementsByTagName("fieldscreenscheme");
		for (int i = 0; i < nl.getLength(); i++) {
			Element el = (Element) nl.item(i);
			String name = el.getAttribute("name");

			FieldScreenScheme fieldScreenScheme = createIfNecessaryAndgetFieldScreenScheme(name);
			log.debug("Initialisiting field screen scheme {}", fieldScreenScheme.getName());
			NodeList operations = el.getElementsByTagName("operation");
			for (int j = 0; j < operations.getLength(); j++) {

				Element operationNode = (Element) operations.item(j);
				String operationKey = operationNode.getAttribute("name");
				FieldScreen fieldScreen = fieldScreens.get(operationNode.getAttribute("value"));
				for (ScreenableIssueOperation operation : getIssueOperationsByName(operationKey)) {
					FieldScreenSchemeItem schemeItem = new FieldScreenSchemeItemImpl(fieldScreenSchemeManager,
							fieldScreenManager);
					schemeItem.setIssueOperation(operation);
					schemeItem.setFieldScreen(fieldScreen);
					fieldScreenScheme.addFieldScreenSchemeItem(schemeItem);
					log.debug("Associating operation {} with screen {}", operation.getNameKey(), fieldScreen.getName()); 
				}
			}
			fieldScreenSchemes.put(fieldScreenScheme.getName(), fieldScreenScheme);
		}

		// Create the issue type schemes and associate them with the field
		// schemes
		log.debug("\n\n ISSUE TYPE SCREEN SCHEMES \n\n");
		nl = doc.getElementsByTagName("issuetypescreenscheme");
		for (int i = 0; i < nl.getLength(); i++) {
			Element el = (Element) nl.item(i);
			String name = el.getAttribute("name");
			IssueTypeScreenScheme issueTypeScreenScheme = createIfNecessaryAndGetIssueTypeScreenScheme(name);

			log.debug("Initialising issue type screen scheme {}.", issueTypeScreenScheme.getName());
			NodeList xmlIssueTypes = el.getElementsByTagName("issuetype");
			for (int j = 0; j < xmlIssueTypes.getLength(); j++) {
				Element xmlIssueType = (Element) xmlIssueTypes.item(j);
				IssueType issueType = createIfNecesseryAndGetIssueType(xmlIssueType.getAttribute("name"));
				FieldScreenScheme fieldScreenScheme = fieldScreenSchemes.get(xmlIssueType.getAttribute("screenscheme"));
				IssueTypeScreenSchemeEntity entity = new IssueTypeScreenSchemeEntityImpl(issueTypeScreenSchemeManager,
						(GenericValue) null, fieldScreenSchemeManager, constantsManager);
				entity.setIssueTypeId(issueType != null ? issueType.getId() : null);
				entity.setFieldScreenScheme(fieldScreenScheme);
				issueTypeScreenScheme.addEntity(entity);
				log.debug("Associating issue type {} with field screen scheme {}.", issueType, fieldScreenScheme);
			}

			String projectKey = ConfigMap.get("project.key");
			Project project = ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey);

			for (GenericValue proj : issueTypeScreenSchemeManager.getProjects(issueTypeScreenScheme)) {
				if (projectKey.equals(proj.get("key"))) {
					log.debug("IssueTypeScreenScheme {} is already associated with project {}",
							issueTypeScreenScheme.getName(), project);
					return;
				}
			}

			if (project != null) {
				log.debug("Setting issue type screen scheme association with project {}", project);
				issueTypeScreenSchemeManager.addSchemeAssociation(project, issueTypeScreenScheme);
			} else {
				log.debug("Project {} does not exist", projectKey);
			}
		}
	}

	/**
	 * 
	 * @param name
	 * @return
	 */
	private CustomField getCustomField(String name) {
		Collection<CustomField> fields = cfm.getCustomFieldObjectsByName(name);
		if (fields == null || fields.isEmpty()) {
			return null;
		}
		return fields.iterator().next();
	}

	/**
	 * 
	 * @param name
	 * @param description
	 * @param type
	 * @param issueTypes
	 * @param options
	 * @return
	 * @throws GenericEntityException
	 */
	private CustomField createIfNeccessaryAndGetCustomField(String name, String description, String type,
			List<IssueType> issueTypes) throws GenericEntityException {

		log.debug("Trying to create custom field {} with type {}", name, type);
		CustomFieldType<?, ?> fieldType = cfm.getCustomFieldType(type);

		String projectKey = ConfigMap.get("holiday.project.key");
		Project project = ComponentAccessor.getProjectManager().getProjectByCurrentKey(projectKey);
		List<JiraContextNode> jiraContextNodes = Arrays.asList(new ProjectContext(project.getId()));

		log.info("Trying to create custom field {} for projects {} and issue types {}", name, projectKey, issueTypes);
		CustomField customField = cfm.createCustomField(name, description, fieldType, null, jiraContextNodes,
				issueTypes);
		log.debug("Created Custom field. Name: {}, Id: {}, NameKey: {}, Class: {}", customField.getName(),
				customField.getId(), customField.getNameKey(), customField.getClass());


		return customField;
	}

	private void setOptionsIfNecessary(CustomField customField, List<String> options) {
		if (options != null && !options.isEmpty()) {
			FieldConfigSchemeManager fieldConfigSchemeManager = ComponentAccessor.getFieldConfigSchemeManager();
			OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
			List<FieldConfigScheme> schemes = fieldConfigSchemeManager.getConfigSchemesForField(customField);
			for (FieldConfigScheme scheme : schemes) {
				FieldConfig config = scheme.getOneAndOnlyConfig();
				List<Option> existingOptions = optionsManager.getOptions(config);
				if (existingOptions == null) {
					existingOptions = new ArrayList<>();
				}
				List<String> existingValues = existingOptions.stream().map(t -> t.getValue())
						.collect(Collectors.toList());
				List<String> optionsToAdd = options.stream().filter(t -> !existingValues.contains(t))
						.collect(Collectors.toList());
				Long sequence = 1L;
				for (String value : optionsToAdd) {
					log.debug("Adding option {}.", value);
					Option option = optionsManager.createOption(config, null, sequence++, value);
					existingOptions.add(option);
					optionsManager.updateOptions(existingOptions);
				}
			}
		}
	}
	
	
	/**
	 * 
	 * @param name
	 * @return
	 */
	private IssueType createIfNecesseryAndGetIssueType(String name) {
		IssueTypeManager issueTypeManager = ComponentAccessor.getComponent(IssueTypeManager.class);
		for (IssueType issueType : issueTypeManager.getIssueTypes()) {
			if (issueType.getName().equals(name)) {
				return issueType;
			}
		}
		String description = "Automatically created on " + new Date();
		return issueTypeManager.createIssueType(name, description, 0L);
	}
	
	/**
	 * 
	 * @param name
	 * @return
	 */
	private FieldScreenScheme createIfNecessaryAndgetFieldScreenScheme(String name) {
		FieldScreenScheme scheme = null;
		for (FieldScreenScheme sc : fieldScreenSchemeManager.getFieldScreenSchemes()) {
			if (name.equals(sc.getName())) {
				scheme = sc;
				break;
			}
		}
		if (scheme == null) {
			scheme = fieldScreenSchemeManager.getFieldScreenScheme(FieldScreen.DEFAULT_SCREEN_ID);
			scheme.setName(name);
			scheme.setDescription("Automatically created");
			scheme.store();
		}
		return scheme;
	}

	/**
	 * 
	 * @param name
	 * @return
	 */
	private IssueTypeScreenScheme createIfNecessaryAndGetIssueTypeScreenScheme(String name) {
		for (IssueTypeScreenScheme iScheme : issueTypeScreenSchemeManager.getIssueTypeScreenSchemes()) {
			if (name.equals(iScheme.getName())) {
				log.debug("Issue type screen scheme '{}' already exists", name);
				return iScheme;
			}
		}
		IssueTypeScreenScheme issueTypeScreenScheme = new IssueTypeScreenSchemeImpl(issueTypeScreenSchemeManager, null);
		issueTypeScreenScheme.setName(name);
		issueTypeScreenScheme.setDescription("Created on " + new Date().toString());
		issueTypeScreenScheme.store();
		return issueTypeScreenScheme;
	}

	/**
	 * 
	 * @param name
	 * @return
	 * @throws GenericEntityException
	 */
	private FieldScreen createIfNecessaryAndGetFieldScreen(String name) throws GenericEntityException {
		for (FieldScreen sc : fieldScreenManager.getFieldScreens()) {
			if (name.equals(sc.getName())) {
				return sc;
			}
		}
		FieldScreen screen = new FieldScreenImpl(fieldScreenManager);
		screen.setName(name);
		screen.setDescription("Created on " + new Date());
		screen.addTab("Tab1");
		screen.store();
		return screen;
	}

	/**
	 * 
	 * @param tab
	 * @param customField
	 */
	private final void addCustomFieldToScreen(FieldScreenTab tab, CustomField customField) {
		String fieldId = customField.getId();
		for (FieldScreenLayoutItem item : tab.getFieldScreenLayoutItems()) {
			if (item.getFieldId().equals(fieldId)) {
				log.info("Field {} already exists in screen", customField);
				return;
			}
		}
		log.debug("Added field {} to tab", customField.getName());
		tab.addFieldScreenLayoutItem(fieldId);
	}

	/**
	 * 
	 * @param screen
	 * @param fieldName
	 */
	private final void addFieldToScreen(FieldScreen screen, String fieldName) {
		FieldScreenTab tab = screen.getTab(0);
		OrderableField<?> field = fieldManager.getOrderableField(fieldName);
		if (field == null) {
			log.info("Addition of unknown field {} is ignored", fieldName);
			return;
		}
		String fieldId = field.getId();
		for (FieldScreenLayoutItem item : tab.getFieldScreenLayoutItems()) {
			if (item.getFieldId().equals(fieldId)) {
				log.info("Field {} already exists in screen", fieldId);
				return;
			}
		}
		tab.addFieldScreenLayoutItem(fieldId);
	}

	/**
	 * 
	 * @return
	 */
	public Map<String, FieldScreen> getAvailableScreens() {
		return fieldScreens;
	}

	/**
	 * 
	 * @param name
	 * @return
	 */
	private List<ScreenableIssueOperation> getIssueOperationsByName(String name) {
		List<ScreenableIssueOperation> result = new ArrayList<>();
		switch (name) {
		case "EDIT":
			result.add(IssueOperations.EDIT_ISSUE_OPERATION);
			break;
		case "CREATE":
			result.add(IssueOperations.CREATE_ISSUE_OPERATION);
			break;
		case "VIEW":
			result.add(IssueOperations.VIEW_ISSUE_OPERATION);
			break;
		default:
			result = Arrays.asList(IssueOperations.EDIT_ISSUE_OPERATION, IssueOperations.CREATE_ISSUE_OPERATION,
					IssueOperations.VIEW_ISSUE_OPERATION);
		}
		return result;
	}

	
	
}
