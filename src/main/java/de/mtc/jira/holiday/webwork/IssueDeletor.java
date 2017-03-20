package de.mtc.jira.holiday.webwork;

import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import de.mtc.jira.holiday.ConfigMap;

@Scanned
public class IssueDeletor extends JiraWebActionSupport {

	private static final long serialVersionUID = 1L;

	@ComponentImport
	private ProjectManager projectManager;
	
	@ComponentImport
	private IssueService issueService;
	
	@ComponentImport
	private JiraAuthenticationContext JiraAuthenticationContext;
	
	@ComponentImport
	private SearchService searchService;
	
	@Autowired
	public IssueDeletor(ProjectManager projectManager, IssueService issueService, JiraAuthenticationContext jiraAuthenticationContext, SearchService searchService) {
		this.projectManager = projectManager;
		this.issueService = issueService;
		this.JiraAuthenticationContext = jiraAuthenticationContext;
		this.searchService = searchService;
	}
	
	@Override
	protected String doExecute() throws Exception {
		ApplicationUser user = JiraAuthenticationContext.getLoggedInUser();
		SearchService.ParseResult parseResult = searchService.parseQuery(user, "project=" + ConfigMap.get("project.key"));
		parseResult.getQuery();
//		searchService.search(user, parseResult.getQuery(), PagerFilter<T>);
//		
//		
//		
//		DeleteValidationResult result = issueService.validateDelete(user, arg1)
	}	
}
