package de.mtc.jira.absence;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.query.Query;

public class AbsenceHistory<V extends Absence> {

	private static final Logger log = LoggerFactory.getLogger(AbsenceHistory.class);
	private ApplicationUser user;
	private List<V> absences;

	public static <T extends Absence> AbsenceHistory<T> getHistory(ApplicationUser user, HistoryParams<T> params)
			throws JiraValidationException {
		AbsenceHistory<T> history = new AbsenceHistory<T>(user);
		history.setVacations(params.filter(history.executeQuery(params.getJqlQuery())));
		return history;
	}

	public AbsenceHistory(ApplicationUser user) {
		this.user = user;
	}

	public void setVacations(List<V> vacations) {
		this.absences = vacations;
	}

	public int getCurrentYear() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		return cal.get(Calendar.YEAR);
	}

	private final List<Issue> executeQuery(Query jqlQuery) throws JiraValidationException {

		List<Issue> result = new ArrayList<>();
		SearchService searchService = ComponentAccessor.getComponentOfType(SearchService.class);
		SearchResults results = null;
		
		log.debug("Exceuting jql query {}, {}", searchService.getJqlString(jqlQuery), jqlQuery.toString());
				
		try {
			results = searchService.search(user, jqlQuery, new com.atlassian.jira.web.bean.PagerFilter<>());
		} catch (SearchException e) {
			throw new JiraValidationException("Couldn't retrieve old issues", e);
		}
		if (results != null) {
			final List<Issue> issues = results.getIssues();
			result.addAll(issues);
			String issueList = issues.stream().map(t -> t.getKey()).collect(Collectors.joining(","));
			log.debug("Result " + issueList);
		}

		log.debug("Found results: {}", result);
		return result;
	}

	public List<V> getAbsences() {
		return absences;
	}

	public Double getSum() {
		double d = 0d;
		for (Absence entry : absences) {
			d += entry.getNumberOfWorkingDays();
		}
		return d;
	}
}
