package de.mtc.jira.holiday;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.context.ProjectContext;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.user.UserDetails;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;

public class ComponentInvestigator extends JiraWebActionSupport {

	private static final long serialVersionUID = 1L;
	private final static Logger log = LoggerFactory.getLogger(ComponentInvestigator.class);

	public CustomFieldManager getCustomFieldManager() {
		return ComponentAccessor.getCustomFieldManager();
	}

	public ConstantsManager getConstantsManager() {
		return ComponentAccessor.getConstantsManager();
	}

	public UserManager getUserManager() {
		return ComponentAccessor.getUserManager();
	}

	public UserPropertyManager getUserPropertyManager() {
		return ComponentAccessor.getUserPropertyManager();
	}

	public String doCreate() {
		log.debug("Creating custom fields");
		// ProjectCreator creator = new ProjectCreator();
//		createCustomField("Start Date", "Start Date of Absence", ProjectCreator.DATE_PICKER);
//		createCustomField("End Date", "End Date of Absence", ProjectCreator.DATE_PICKER);
//		createCustomField("Jahresurlaub", "Jahresurlaub", ProjectCreator.READ_ONLY);
//		createCustomField("Resturlaub", "Resturlaub", ProjectCreator.READ_ONLY);
//		
//		createUser("teamlead", "Team Lead");
//		createUser("manager", "Manager");
		return "created";
	}
	
	@Override
	protected String doExecute() throws Exception {
		log.debug("Excecuting function");
		return SUCCESS;
	}

	@Override
	public String doDefault() throws Exception {
		log.debug("Excecuting default");
		return super.doDefault();
	}

	
	public final static String TEXT_SINGLE_LINE = "com.atlassian.jira.plugin.system.customfieldtypes:textfield";
	public final static String DATE_PICKER = "com.atlassian.jira.plugin.system.customfieldtypes:datepicker";
	public final static String SELECT = "com.atlassian.jira.plugin.system.customfieldtypes:select";
	public final static String READ_ONLY = "com.atlassian.jira.plugin.system.customfieldtypes:readonlyfield";
	
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
			StringBuilder sb = new StringBuilder();
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
	
	
	public void createUser(String name, String displayName) {
		UserManager um = ComponentAccessor.getUserManager();
		if(um.getUserByName(name) != null) {
			log.info("User " + name + " already exists");
			return;
		}
		try {
			ComponentAccessor.getUserManager().createUser(new UserDetails(name, displayName));
		} catch (CreateException | PermissionException e) {
			log.error("Could nor create user " + name, e);
		}
	}
}
