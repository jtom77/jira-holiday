package de.mtc.jira.holiday.webwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;

import de.mtc.jira.holiday.ConfigMap;
import de.mtc.jira.holiday.JiraValidationException;
import de.mtc.jira.holiday.Vacation;

public class VacationWatcher extends JiraWebActionSupport {
	
	private static final long serialVersionUID = 1L;


	private final static Logger log = LoggerFactory.getLogger(VacationWatcher.class);
	
	
	private List<Vacation> vacations;
	
	public List<Vacation> getVacations() {
		return vacations;
	}
	
	@Override
	protected String doExecute() throws Exception {
		System.out.println("Executing main method");
		ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
		Map<String, String> replacements = new HashMap<String, String>();
		replacements.put("reporter", user.getKey());
		replacements.put("created", "2015-01-01");
		String jql = ConfigMap.get("holiday-history.jqlquery", replacements);
		List<Issue> result = new ArrayList<>();
		SearchService searchService = ComponentAccessor.getComponentOfType(SearchService.class);
		SearchService.ParseResult parseResult = searchService.parseQuery(user, jql);
		if (parseResult.isValid()) {
			SearchResults results = null;
			try {
				results = searchService.search(user, parseResult.getQuery(),
						new com.atlassian.jira.web.bean.PagerFilter<>());
			} catch (SearchException e) {
				throw new JiraValidationException("Couldn't retrieve old issues", e);
			}
			if (results != null) {
				final List<Issue> issues = results.getIssues();
				result.addAll(issues);
				String issueList = issues.stream().map(t -> t.getKey()).collect(Collectors.joining(","));
				log.debug("Result " + issueList);
			}
		} else {
			log.debug("Search result not valid " + parseResult.getErrors());
		}

		log.debug("Found results: {}", result);

		
		vacations = new ArrayList<>();
		for(Issue issue : result) {
			vacations.add(new Vacation(issue));
		}
	
		ComponentAccessor.getProjectManager().getProjectByCurrentKey("ISF");
		
		return SUCCESS;
	}
	

}
