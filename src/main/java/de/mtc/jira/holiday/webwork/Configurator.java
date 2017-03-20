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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.config.StatusCategoryManager;
import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.context.ProjectContext;
import com.atlassian.jira.issue.customfields.CustomFieldSearcher;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.OrderableField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenImpl;
import com.atlassian.jira.issue.fields.screen.FieldScreenLayoutItem;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreenSchemeImpl;
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
import com.atlassian.jira.issue.managers.CustomFieldSearcherManager;
import com.atlassian.jira.issue.operation.IssueOperations;
import com.atlassian.jira.issue.operation.ScreenableIssueOperation;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.status.category.StatusCategory;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.workflow.AssignableWorkflowScheme;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowScheme;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.jira.workflow.migration.AssignableWorkflowSchemeMigrationHelper;
import com.atlassian.jira.workflow.migration.MigrationHelperFactory;

import de.mtc.jira.holiday.ConfigMap;

public class Configurator {

	private final static Logger log = LoggerFactory.getLogger(Configurator.class);

	private final FieldScreenManager fieldScreenManager;
	private final FieldScreenSchemeManager fieldScreenSchemeManager;
	private final FieldManager fieldManager;
	private final IssueTypeScreenSchemeManager issueTypeScreenSchemeManager;
	private final ConstantsManager constantsManager;
	private final CustomFieldManager cfm;
	private final WorkflowSchemeManager workflowSchemeManager;

	private final Map<String, CustomField> customFields = new HashMap<>();
	private final Map<String, FieldScreen> fieldScreens = new HashMap<>();
	private final Map<String, FieldScreenScheme> fieldScreenSchemes = new HashMap<>();
	private final Map<String, JiraWorkflow> workflows = new HashMap<>();
	private final Map<String, IssueType> issueTypes = new HashMap<>();
	private final Map<String, Status> statuses = new HashMap<>();

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
		workflowSchemeManager = ComponentAccessor.getWorkflowSchemeManager();
		constantsManager = ComponentAccessor.getConstantsManager();
		cfm = ComponentAccessor.getCustomFieldManager();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		doc = db.parse(Configurator.class.getClassLoader().getResourceAsStream("fielddescription.xml"));

		// Create issue types
		log.debug("\n\n ISSUE TYPES \n\n");
		for (Element el : getElements("issuetypes", "issuetype")) {
			String name = el.getAttribute("name");
			issueTypes.put(name, createIfUndefinedAndGetIssueType(name));
		}

		// Create custom fields
		log.debug("\n\n CUSTOM FIELDS \n\n");
		for (Element el : getElements("customfields", "customfield")) {
			String name = el.getAttribute("name");
			String type = el.getAttribute("type");
			CustomField cf = getCustomField(name);
			if (cf == null) {
				String description = "Automatically created";
				cf = createIfUndefinedAndGetCustomField(name, description, type, new ArrayList<>(issueTypes.values()));
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
		for (Element el : getElements("fieldscreens", "fieldscreen")) {
			FieldScreen fieldScreen = createIfUndefinedAndGetFieldScreen(el.getAttribute("name"));
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
		for (Element el : getElements("fieldscreenschemes", "fieldscreenscheme")) {
			String name = el.getAttribute("name");

			FieldScreenScheme fieldScreenScheme = createIfUndefinedAndGetFieldScreenScheme(name);
			log.debug("Initialisiting field screen scheme {}", fieldScreenScheme.getName());
			NodeList operations = el.getElementsByTagName("association");
			for (int j = 0; j < operations.getLength(); j++) {

				Element operationNode = (Element) operations.item(j);
				String operationKey = operationNode.getAttribute("operation");
				FieldScreen fieldScreen = fieldScreens.get(operationNode.getAttribute("fieldscreen"));
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
		for (Element el : getElements("issuetypescreenschemes", "issuetypescreenscheme")) {
			String name = el.getAttribute("name");
			IssueTypeScreenScheme issueTypeScreenScheme = createIfUndefinedAndGetIssueTypeScreenScheme(name);

			log.debug("Initialising issue type screen scheme {}.", issueTypeScreenScheme.getName());
			NodeList xmlIssueTypes = el.getElementsByTagName("association");
			
			for (int j = 0; j < xmlIssueTypes.getLength(); j++) {
				Element xmlIssueType = (Element) xmlIssueTypes.item(j);
				IssueType issueType = createIfUndefinedAndGetIssueType(xmlIssueType.getAttribute("issuetype"));
				FieldScreenScheme fieldScreenScheme = fieldScreenSchemes.get(xmlIssueType.getAttribute("screenscheme"));
				IssueTypeScreenSchemeEntity entity = new IssueTypeScreenSchemeEntityImpl(issueTypeScreenSchemeManager,
						(GenericValue) null, fieldScreenSchemeManager, constantsManager);
				entity.setIssueTypeId(issueType != null ? issueType.getId() : null);
				entity.setFieldScreenScheme(fieldScreenScheme);
				issueTypeScreenScheme.addEntity(entity);
				log.debug("Associating issue type {} with field screen scheme {}.", issueType.getName(), fieldScreenScheme.getName());
				// issueTypeScreenScheme.store();
			}

			String projectKey = ConfigMap.get("project.key");
			Project project = ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey);

			boolean alreadyAssociated = false;
			for (GenericValue proj : issueTypeScreenSchemeManager.getProjects(issueTypeScreenScheme)) {
				if (projectKey.equals(proj.get("key"))) {
					log.debug("IssueTypeScreenScheme {} is already associated with project {}",
							issueTypeScreenScheme.getName(), project);
					alreadyAssociated = true;
					break;
				}
			}

			if (project != null && !alreadyAssociated) {
				log.debug("Setting issue type screen scheme association with project {}", project);
				issueTypeScreenSchemeManager.addSchemeAssociation(project, issueTypeScreenScheme);
			} else {
				log.debug("Project {} does not exist", projectKey);
			}
		}

		log.debug("\n\n STATUSES \n\n");
		for (Element el : getElements("statuses", "status")) {
			String name = el.getAttribute("name");
			String category = el.getAttribute("category");
			statuses.put(name, createIfUndefinedAndGetStatus(name, category));
		}
		
		
		log.debug("\n\n WORKFLOWS \n\n");
		for (Element el : getElements("workflows", "workflow")) {
			String name = el.getAttribute("name");
			String path = el.getAttribute("path");
			WorkflowCreator creator = new WorkflowCreator();
			log.debug("Creating Workflow {}", name);
			JiraWorkflow workflow = creator.createWorkflow(name, path, fieldScreens, statuses);
			workflows.put(workflow.getName(), workflow);
		}

		log.debug("\n\n WORKFLOWSCHEMES \n\n");
		for (Element el : getElements("workflowschemes", "workflowscheme")) {
			NodeList elAssociations = el.getElementsByTagName("association");
			Map<String, String> itemTypeIdToWorkflow = new HashMap<>();
			for (int j = 0; j < elAssociations.getLength(); j++) {
				Element elAssociation = (Element) elAssociations.item(j);
				String issueTypeName = elAssociation.getAttribute("issuetype");
				String issueTypeId = issueTypes.get(issueTypeName).getId();
				String workflowName = elAssociation.getAttribute("workflow");
				log.debug("Associating issueType {} [{}] with workflow {}", issueTypeName, issueTypeId, workflowName);
				itemTypeIdToWorkflow.put(issueTypeId, workflowName);
			}
			String workflowSchemeName = el.getAttribute("name");
			WorkflowScheme workflowScheme = createWorkflowScreenSchemeIfNecessary(workflowSchemeName,
					itemTypeIdToWorkflow);
			// workflowSchemeManager.set
			String projectKey = ConfigMap.get("project.key");
			ProjectManager projectManager = ComponentAccessor.getProjectManager();
			Project project = projectManager.getProjectByCurrentKey(projectKey);

			MigrationHelperFactory migrationHelperFactory = ComponentAccessor
					.getComponent(MigrationHelperFactory.class);
			AssignableWorkflowSchemeMigrationHelper migrationHelper = migrationHelperFactory
					.createMigrationHelper(project, (AssignableWorkflowScheme) workflowScheme);
			migrationHelper.associateProjectAndWorkflowScheme();
		}

		log.debug("\n\n ISSUE TYPE SCHEMES \n\n");
		for (Element el : getElements("issuetypeschemes", "issuetypeschemes")) {
			List<String> issueTypeIds = new ArrayList<>();
			NodeList nl2 = el.getElementsByTagName("item");
			for (int j = 0; j < nl2.getLength(); j++) {
				Element elIssueType = (Element) nl2.item(j);
				String issueTypeId = issueTypes.get(elIssueType.getAttribute("ref")).getId();
				issueTypeIds.add(issueTypeId);
			}
			String name = el.getAttribute("issuetype");
			log.debug("Initialising issue type scheme {} -> {}", name, issueTypeIds);
			FieldConfigScheme fieldConfigScheme = createIfUndefinedAndGetFieldConfigScheme(name, issueTypeIds);
		}
	}

	private List<Element> getElements(String el1, String el2) {
		List<Element> result = new ArrayList<>();
		NodeList nl = null;
		if (el2 == null) {
			nl = doc.getElementsByTagName(el1);
		} else {
			nl = ((Element) doc.getElementsByTagName(el1).item(0)).getElementsByTagName(el2);
		}
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element) {
				result.add((Element) node);
			}
		}
		return result;
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
	private CustomField createIfUndefinedAndGetCustomField(String name, String description, String type,
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

		CustomFieldSearcherManager searcherManager = ComponentAccessor.getComponent(CustomFieldSearcherManager.class);
		for(CustomFieldSearcher searcher : searcherManager.getSearchersValidFor(fieldType)) {
			log.debug("Valid searcher for field {}: {}", name, searcher);
		}

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
	private IssueType createIfUndefinedAndGetIssueType(String name) {
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
	private FieldScreenScheme createIfUndefinedAndGetFieldScreenScheme(String name) {
		FieldScreenScheme scheme = null;
		for (FieldScreenScheme sc : fieldScreenSchemeManager.getFieldScreenSchemes()) {
			if (name.equals(sc.getName())) {
				log.debug("FieldScreenScheme {} already exists", name);
				scheme = sc;
				break;
			}
		}

		if (scheme == null) {
			log.debug("Creating Field Screen Scheme {}", name);
			scheme = new FieldScreenSchemeImpl(fieldScreenSchemeManager);
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
	private IssueTypeScreenScheme createIfUndefinedAndGetIssueTypeScreenScheme(String name) {
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
	private FieldScreen createIfUndefinedAndGetFieldScreen(String name) throws GenericEntityException {
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

	private FieldConfigScheme createIfUndefinedAndGetFieldConfigScheme(String name, List<String> issueTypeIds) {
		IssueTypeSchemeManager issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager();
		for (FieldConfigScheme fieldConfigScheme : issueTypeSchemeManager.getAllSchemes()) {
			if (name.equals(fieldConfigScheme.getName())) {
				Collection<String> existingIds = fieldConfigScheme.getAssociatedIssueTypeIds();
				for (String id : issueTypeIds) {
					if (!existingIds.contains(id)) {
						existingIds.add(id);
					}
				}
				return fieldConfigScheme;
			}
		}
		String desc = "Automatically created on " + new Date();
		return issueTypeSchemeManager.create(name, desc, issueTypeIds);

	}

	private WorkflowScheme createWorkflowScreenSchemeIfNecessary(String name,
			Map<String, String> issueTypeIdToWorfklowName) {
		for (AssignableWorkflowScheme scheme : workflowSchemeManager.getAssignableSchemes()) {
			if (scheme.getName().equals(name)) {
				// Map<String, String> mappings = scheme.getMappings();
				// for (String key : issueTypeIdToWorfklowName.keySet()) {
				// mappings.putIfAbsent(key,
				// issueTypeIdToWorfklowName.get(key));
				// }
				return scheme;
			}
		}
		AssignableWorkflowScheme.Builder builder = workflowSchemeManager.assignableBuilder();
		builder.setName(name);
		builder.setDescription("Automatically created on " + new Date());
		builder.setMappings(issueTypeIdToWorfklowName);
		WorkflowScheme scheme = workflowSchemeManager.createScheme(builder.build());
		log.debug("Created workflow scheme {} [{}]", scheme.getName(), scheme.getId(), scheme.getMappings());
		return scheme;
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

	public static Status createIfUndefinedAndGetStatus(String name, String statusCategoryKey) {
		
		String categoryKey = null;
		switch(statusCategoryKey) {
		case "UNDEFINED":
			categoryKey = StatusCategory.UNDEFINED;
			break;
		case "TO_DO":
			categoryKey = StatusCategory.TO_DO;
			break;
		case "IN_PROGRESS":
			categoryKey = StatusCategory.IN_PROGRESS;
			break;
		case "COMPLETE":
			categoryKey = StatusCategory.COMPLETE;
			break;
		default:
			categoryKey = StatusCategory.UNDEFINED;
		}
		
		StatusManager statusManager = ComponentAccessor.getComponent(StatusManager.class);
		StatusCategoryManager statusCategoryManager = ComponentAccessor.getComponent(StatusCategoryManager.class);
		StatusCategory statusCategory = statusCategoryManager.getStatusCategoryByKey(categoryKey);
		for (Status status : statusManager.getStatuses()) {
			if (status.getName().equals(name)) {
				if (!status.getStatusCategory().getKey().equals(statusCategoryKey)) {
					statusManager.editStatus(status, status.getName(), status.getDescription(), status.getIconUrlHtml(),
							statusCategory);
				}
				return status;
			}
		}
		
		return statusManager.createStatus(name, "Automatically created on " + new Date(),
				"/images/icons/pluginIcon.png", statusCategory);
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
