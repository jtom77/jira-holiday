package de.mtc.jira.holiday.webwork;

import java.util.ArrayList;
import java.util.List;

import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.OrderableField;
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
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeImpl;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.issue.operation.IssueOperations;

import de.mtc.jira.holiday.WorkflowHelper;

public class FieldScreenCreator {

	private final static Logger log = LoggerFactory.getLogger(FieldScreenCreator.class);
	
	private final FieldScreenManager fieldScreenManager;
	private final FieldScreenSchemeManager fieldScreenSchemeManager;
	private final FieldManager fieldManager;
	private final IssueTypeScreenSchemeManager issueTypeScreenSchemeManager;
	private final CustomFieldManager customFieldManager;
	private final ConstantsManager constantsManager;
	
	private final static String FS_CREATE_NAME;		
	private final static String FS_CREATE_DESCRIPTION;
	private final static String FS_SCHEME_CREATE_NAME;
	private final static String FS_SCHEME_CREATE_DESCRIPTION;
	private final static String ISSUETYPE_SCREEN_NAME;
	private final static String ISSUETYPE_SCREEN_SCHEME;

	static {
		FS_CREATE_NAME = WorkflowHelper.getProperty("fieldscreen.create.name");		
		FS_CREATE_DESCRIPTION = WorkflowHelper.getProperty("fieldscreen.create.description");
		FS_SCHEME_CREATE_NAME = WorkflowHelper.getProperty("fieldscreen_scheme.name");
		FS_SCHEME_CREATE_DESCRIPTION = WorkflowHelper.getProperty("fieldscreen_scheme.description");
		ISSUETYPE_SCREEN_NAME = WorkflowHelper.getProperty("issuetype.screen_scheme.name");
		ISSUETYPE_SCREEN_SCHEME = WorkflowHelper.getProperty("issuetype.screen_scheme.description");
	}
	

	public FieldScreenCreator() {
		fieldScreenManager = ComponentAccessor.getFieldScreenManager();
		fieldScreenSchemeManager = ComponentAccessor.getComponent(FieldScreenSchemeManager.class);
		fieldManager = ComponentAccessor.getFieldManager();
		issueTypeScreenSchemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager();
		constantsManager = ComponentAccessor.getConstantsManager();
		customFieldManager = ComponentAccessor.getCustomFieldManager();
	}
	
	public void deleteFieldScreens() {
		List<FieldScreen> toRemove = new ArrayList<>();
		for(FieldScreen sc : fieldScreenManager.getFieldScreens()) {
			if(FS_CREATE_NAME.equals(sc.getName()) || "myName".equals(sc.getName())) {
				toRemove.add(sc);
			}
		}
		for(FieldScreen sc : toRemove) {
			log.debug("Removing Fieldscreen {} with id {}", sc.getName(), sc.getId());
			fieldScreenManager.removeFieldScreen(sc.getId());
		}
	}
	
	public FieldScreen getFieldScreenById(Object o) {
		Long id = null;
		if(o instanceof Number) {
			id = ((Number) o).longValue();
		} else if(o instanceof String) {
			id = Long.parseLong(o.toString());
		} else {
			throw new IllegalArgumentException();
		}
		for(FieldScreen sc : fieldScreenManager.getFieldScreens()) {
			if(sc.getId() == id) {
				return sc;
			}
		}
		return null;
	}
	

	public FieldScreen createFieldScreen() throws GenericEntityException {
		
		CustomFieldCreator customFieldCreator = new CustomFieldCreator();
		customFieldCreator.createAllFields();
		List<CustomField> customFields = customFieldCreator.getCustomFields();
		
		FieldScreen screen = null;
		for(FieldScreen sc : fieldScreenManager.getFieldScreens()) {
			if(FS_CREATE_NAME.equals(sc.getName())) {
				screen = sc;
				break;
			}
		}
		
		if(screen==null) {
			screen = new FieldScreenImpl(fieldScreenManager);
			screen.setName(FS_CREATE_NAME);
			screen.setDescription(FS_CREATE_DESCRIPTION);
			screen.store();
			screen.addTab("Tab1");
		}
		
		FieldScreenTab tab = screen.getTab(0);
		
		addFieldToScreen(tab, "summary");
		addFieldToScreen(tab, "reporter");

		for(CustomField cf : customFields) {
			addCustomFieldToScreen(tab, cf);
		}
		
		log.debug("Configured field screen {} with id {}", screen, screen.getId());
		
		return screen;
	}
	
	
	private final void addCustomFieldToScreen(FieldScreenTab tab, CustomField customField) {
		String fieldId = customField.getId();
		for(FieldScreenLayoutItem item : tab.getFieldScreenLayoutItems()) {
			if(item.getFieldId().equals(fieldId)) {
				log.info("Field {} already exists in screen", fieldId);
				return;
			}
		}
		log.debug("Added field {} to tab", customField.getName());
		tab.addFieldScreenLayoutItem(fieldId);
	}
	
	private final void addFieldToScreen(FieldScreenTab tab, String fieldName) {
		OrderableField<?> field = fieldManager.getOrderableField(fieldName);
		if(field == null) {
			log.info("Addition of unknown field {} is ignored", fieldName);
			return;
		}
		String fieldId = field.getId();
		for(FieldScreenLayoutItem item : tab.getFieldScreenLayoutItems()) {
			if(item.getFieldId().equals(fieldId)) {
				log.info("Field {} already exists in screen", fieldId);
				return;
			}
		}
		
		log.debug("Added field {} to tab", fieldName);
		tab.addFieldScreenLayoutItem(fieldId);
	}
	
	
	private FieldScreenScheme createFieldScreenScheme(FieldScreen screen) {
		FieldScreenScheme scheme = fieldScreenSchemeManager.getFieldScreenScheme(FieldScreen.DEFAULT_SCREEN_ID);
		scheme.setName(FS_SCHEME_CREATE_NAME);
		scheme.setDescription(FS_SCHEME_CREATE_DESCRIPTION);
		scheme.store();

		FieldScreenSchemeItem mySchemeItem = new FieldScreenSchemeItemImpl(fieldScreenSchemeManager,
				fieldScreenManager);
		
		mySchemeItem.setIssueOperation(IssueOperations.CREATE_ISSUE_OPERATION); // or:
																				// EDIT_ISSUE_OPERATION,
																				// VIEW_ISSUE_OPERATION
		mySchemeItem.setFieldScreen(screen);
		scheme.addFieldScreenSchemeItem(mySchemeItem);
		return scheme;
	}
	
	
	private void createFieldScreen1() {
		// component dependencies, get via Constructor Injection

		// create screen
		FieldScreen myScreen = new FieldScreenImpl(fieldScreenManager);
		myScreen.setName("myName");
		myScreen.setDescription("myDescription");

		myScreen.store();

		// create tab
		FieldScreenTab myTab = myScreen.addTab("myTabName");
		OrderableField myField = fieldManager.getOrderableField("assignee"); // e.g.
																				// "assignee",
																				// "customfield_12345"
		myTab.addFieldScreenLayoutItem(myField.getId());

		FieldScreenScheme myScheme = fieldScreenSchemeManager.getFieldScreenScheme(FieldScreen.DEFAULT_SCREEN_ID);
		myScheme.setName("mySchemeName2");
		myScheme.setDescription("mySchemeDescription");
		myScheme.store();

		// add screen
		FieldScreenSchemeItem mySchemeItem = new FieldScreenSchemeItemImpl(fieldScreenSchemeManager,
				fieldScreenManager);
		mySchemeItem.setIssueOperation(IssueOperations.CREATE_ISSUE_OPERATION); // or:
																				// EDIT_ISSUE_OPERATION,
																				// VIEW_ISSUE_OPERATION
		mySchemeItem.setFieldScreen(myScreen);
		myScheme.addFieldScreenSchemeItem(mySchemeItem);

		// create issueTypeScreenScheme
		IssueTypeScreenScheme myIssueTypeScreenScheme = new IssueTypeScreenSchemeImpl(issueTypeScreenSchemeManager,
				null);
		myIssueTypeScreenScheme.setName("myIssueTypeScreenSchemeName");
		myIssueTypeScreenScheme.setDescription("myIssueTypeScreenSchemeDescription");
		myIssueTypeScreenScheme.store();

		// add scheme to issueTypeScreenScheme
		// IssueTypeScreenSchemeEntity myEntity = new
		// IssueTypeScreenSchemeEntityImpl(
		// issueTypeScreenSchemeManager, (GenericValue) null,
		// fieldScreenSchemeManager, constantsManager);
		// IssueType issueType;
		// myEntity.setIssueTypeId(issueType != null ? issueType.getId() :
		// null); // an entity can be for all IssueTypes (-> null), or just for
		// 1
		// myEntity.setFieldScreenScheme(myScheme);
		// myIssueTypeScreenScheme.addEntity(myEntity);
		//
		// // assign to project
		// Project project = null;
		// issueTypeScreenSchemeManager.addSchemeAssociation(project,
		// myIssueTypeScreenScheme);
	}

}
