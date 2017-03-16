package de.mtc.jira.holiday.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.project.ProjectCreationData;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.AssigneeTypes;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.ApplicationUser;

import de.mtc.jira.holiday.ConfigMap;
import de.mtc.jira.holiday.PropertyHelper;

public class ProjectCreator {

	private final static Logger log = LoggerFactory.getLogger(ProjectCreator.class);
	
	public void createProject() {		
		
		String projectKey = ConfigMap.get("holiday.project.key");
		
		log.debug("\n\nCreating project {}\n\n", projectKey);
		ProjectManager projectManager = ComponentAccessor.getProjectManager();
		Project project = projectManager.getProjectByCurrentKey(projectKey);
		ApplicationUser user = ComponentAccessor.getUserManager().getUserByName("admin");
						
		if (project == null) {
			log.debug("Building project with leader " + user);
			ProjectCreationData.Builder builder = new ProjectCreationData.Builder();
			builder.withKey(projectKey);
			builder.withName(ConfigMap.get("holiday.project.name"));
			builder.withLead(user);
			builder.withAssigneeType(AssigneeTypes.PROJECT_LEAD);
			builder.withAvatarId(null);
			builder.withDescription("Automatically created");
			builder.withUrl("http://www.test.com/");
			project = ComponentAccessor.getProjectManager().createProject(user, builder.build());
		}
		
		checkProperties(user);
	}
	
	private void checkProperties(ApplicationUser user) {
		PropertyHelper propHelper = new PropertyHelper(user);
		String propKey = ConfigMap.PROP_ANNUAL_LEAVE;
		if(propHelper.exists(propKey)) {
			propHelper.set(propKey, 120.0D);
		} else {
			propHelper.getProps().setDouble(propKey, 120.0D);
		}
		log.debug("\nAdjusted Properties {}",propHelper.getProps());
	}
	
}
