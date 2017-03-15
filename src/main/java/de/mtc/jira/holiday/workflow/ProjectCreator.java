package de.mtc.jira.holiday.workflow;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.project.ProjectCreationData;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.IssueTypeManager;
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
		ProjectManager projectManager = ComponentAccessor.getProjectManager();
		Project project = projectManager.getProjectByCurrentKey(projectKey);
		ApplicationUser user = ComponentAccessor.getUserManager().getUser("admin");

		PropertyHelper propHelper = new PropertyHelper(user);
		String propKey = ConfigMap.PROP_ANNUAL_LEAVE;
		if(propHelper.exists(propKey)) {
			propHelper.set(propKey, 120.0D);
		} else {
			propHelper.getProps().setDouble(propKey, 120.0D);
		}
				
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

		IssueTypeManager issueTypeManager = ComponentAccessor.getComponent(IssueTypeManager.class);
		List<String> availableIssueTypeNames = issueTypeManager.getIssueTypes().stream().map(t -> t.getName()).collect(Collectors.toList());
		for (String key : ConfigMap.getMap().keySet()) {
			if (key.startsWith("issuetype")) {
				String name = ConfigMap.get(key);
				if (!availableIssueTypeNames.contains(name)) {
					String description = "Automatically created on " + new Date();
					ComponentAccessor.getComponent(IssueTypeManager.class).createIssueType(name, description, 0L);
					availableIssueTypeNames.add(name);
				}
			}
		}
	}
}
