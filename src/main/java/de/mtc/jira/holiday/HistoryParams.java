package de.mtc.jira.holiday;

import java.util.List;

import com.atlassian.jira.issue.Issue;
import com.atlassian.query.Query;

public interface HistoryParams<V extends Absence> {
	
	List<V> filter(List<Issue> issues);
	
	Query getJqlQuery();
	
}
