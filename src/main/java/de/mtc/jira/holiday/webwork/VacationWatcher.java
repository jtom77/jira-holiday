package de.mtc.jira.holiday.webwork;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.query.Query;

import de.mtc.jira.holiday.AbsenceHistory;
import de.mtc.jira.holiday.HistoryParams;
import de.mtc.jira.holiday.PropertyHelper;
import de.mtc.jira.holiday.TimeSpan;
import de.mtc.jira.holiday.Vacation;
import webwork.action.ActionContext;

@Scanned
public class VacationWatcher extends JiraWebActionSupport implements HistoryParams<Vacation> {

	private static final long serialVersionUID = 1L;

	private final static Logger log = LoggerFactory.getLogger(VacationWatcher.class);

	private ApplicationUser user;
	private String userKey;

	@ComponentImport
	private JiraAuthenticationContext jiraAuthenticationContext;

	@Autowired
	public VacationWatcher(JiraAuthenticationContext jiraAuthenticationContext) {
		this.jiraAuthenticationContext = jiraAuthenticationContext;
		this.user = jiraAuthenticationContext.getLoggedInUser();
		this.userKey = user.getKey();
		log.debug("Injected: " + user);
	}

	private AbsenceHistory<Vacation> absenceHistory;

	public void setUserKey(String userKey) {
		this.userKey = userKey;
	}

	public String getUserKey() {
		return userKey;
	}

	public ApplicationUser getUser() {
		return user;
	}

	public Double getAnnualLeave() {
		return new PropertyHelper(user).getAnnualLeaveAsDouble();
	}

	public AbsenceHistory<Vacation> getAbsenceHistory() {
		return absenceHistory;
	}

	@Override
	protected String doExecute() throws Exception {
		Object sessionUser = ActionContext.getSession().get("vacation-request-user");
		if (sessionUser != null && (sessionUser instanceof ApplicationUser)) {
			this.user = (ApplicationUser) sessionUser;
		} else {
			this.user = jiraAuthenticationContext.getLoggedInUser();
		}
		absenceHistory = AbsenceHistory.getHistory(user, this);
		return SUCCESS;
	}

	@SuppressWarnings("unchecked")
	@Override
	public String doDefault() throws Exception {
		if (userKey != null && !userKey.isEmpty()) {
			user = ComponentAccessor.getUserManager().getUserByKey(userKey);
		}
		if (user == null) {
			user = jiraAuthenticationContext.getLoggedInUser();
		}
		ActionContext.getSession().put("vacation-request-user", user);
		return INPUT;
	}

	@Override
	public List<Vacation> filter(List<Issue> issues) {
		List<Vacation> vacations = new ArrayList<>();
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int year = cal.get(Calendar.YEAR);
		cal.set(year, 0, 1);
		Date startOfYear = cal.getTime();
		for (Issue issue : issues) {
			try {
				Vacation vacation = new Vacation(issue);
				if (startOfYear.compareTo(vacation.getEndDate()) > 0) {
					log.debug("Not adding entry {} > {}", startOfYear, vacation.getEndDate());
					continue;
				} else if (startOfYear.compareTo(vacation.getStartDate()) > 0) {
					vacation.setStartDate(startOfYear);
					TimeSpan timespan = new TimeSpan(getUser(), startOfYear, vacation.getEndDate());
					double numWorkingDays = timespan.getNumberOfWorkingDays();
					log.debug("Setting numberOfWorkingDays {}", numWorkingDays);
					vacation.setNumberOfWorkingDays((vacation.isHalfDay() ? 0.5 : 1.0) * numWorkingDays);
				}
				vacations.add(vacation);
			} catch (Exception e) {
				log.error("Unable to get entry for issue {}, {}", issue, e.getMessage());
			}
		}
		return vacations;
	}

	@Override
	public Query getJqlQuery() {
		String jqlQuery = "type=\"Urlaubsantrag\" and reporter={user} and \"Finish\" > startOfYear() and status = \"Approved\" order by \"Start\"";
		jqlQuery = jqlQuery.replace("{user}", "\"" + getUser().getKey() + "\"");
		SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
		SearchService.ParseResult parseResult = searchService.parseQuery(getUser(), jqlQuery);
		return parseResult.getQuery();
	}
}
