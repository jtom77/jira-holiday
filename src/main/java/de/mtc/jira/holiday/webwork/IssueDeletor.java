package de.mtc.jira.holiday.webwork;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.IssueService.DeleteValidationResult;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import de.mtc.jira.holiday.ConfigMap;

@Scanned
public class IssueDeletor extends JiraWebActionSupport {

	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(IssueDeletor.class);

	private List<String> issueKeys;
	private String projectKey;

	@ComponentImport
	private ProjectManager projectManager;

	@ComponentImport
	private IssueService issueService;

	@ComponentImport
	private JiraAuthenticationContext jiraAuthenticationContext;

	@ComponentImport
	private SearchService searchService;

	@Autowired
	public IssueDeletor(ProjectManager projectManager, IssueService issueService,
			JiraAuthenticationContext jiraAuthenticationContext, SearchService searchService) {
		this.projectManager = projectManager;
		this.issueService = issueService;
		this.jiraAuthenticationContext = jiraAuthenticationContext;
		this.searchService = searchService;
		this.projectKey = ConfigMap.get("project.key");
		
	}

	public List<String> getIssueKeys() {
		return issueKeys;
	}
	
	public String getProjectKey() {
		return projectKey;
	}

	@Override
	public String doDefault() throws Exception {
		return INPUT;
	}
	
	@Override
	protected String doExecute() throws Exception {
		issueKeys = new ArrayList<>();
		ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();
		JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
		builder.where().project(projectKey).and().reporterUser(user.getKey());
		SearchResults searchResults = searchService.search(user, builder.buildQuery(), new PagerFilter<>());
		for (Issue issue : searchResults.getIssues()) {
			issueKeys.add(issue.getKey());
			DeleteValidationResult validationResult = issueService.validateDelete(user, issue.getId());
			issueService.delete(user, validationResult);
			log.debug("Deleting issue {}", issue);
		}
		return SUCCESS;
	}
}
