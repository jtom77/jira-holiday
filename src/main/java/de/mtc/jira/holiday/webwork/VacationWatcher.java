package de.mtc.jira.holiday.webwork;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.query.Query;
import com.atlassian.query.order.SortOrder;

import de.mtc.jira.holiday.Absence;
import de.mtc.jira.holiday.AbsenceHistory;
import de.mtc.jira.holiday.HistoryParams;
import de.mtc.jira.holiday.PropertyHelper;
import de.mtc.jira.holiday.Sickness;
import de.mtc.jira.holiday.Timespan;
import de.mtc.jira.holiday.Vacation;

@Scanned
public class VacationWatcher extends JiraWebActionSupport implements HistoryParams<Absence> {

	private static final long serialVersionUID = 1L;

	private final static Logger log = LoggerFactory.getLogger(VacationWatcher.class);

	private ApplicationUser user;
	private String userKey;
	private List<Vacation> vacations;
	private List<Sickness> sicknesses;
	private double sum;
	private int currentYear;
	private String queryString;

	@ComponentImport
	private JiraAuthenticationContext jiraAuthenticationContext;

	@ComponentImport
	private CustomFieldManager cfm;

	@Autowired
	public VacationWatcher(JiraAuthenticationContext jiraAuthenticationContext, CustomFieldManager cfm) {
		this.jiraAuthenticationContext = jiraAuthenticationContext;
		this.user = jiraAuthenticationContext.getLoggedInUser();
		this.userKey = user.getKey();
		this.cfm = cfm;
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		currentYear = cal.get(Calendar.YEAR);
	}

	public Double getSum() {
		return sum;
	}

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

	public int getCurrentYear() {
		return currentYear;
	}

	public String getQueryString() {
		return queryString;
	}

	public List<Vacation> getVacations() {
		return vacations;
	}

	public List<Sickness> getSicknesses() {
		return sicknesses;
	}

	@Override
	protected String doExecute() throws Exception {
		String key = getUserKey();
		if (key != null && !key.isEmpty()) {
			this.user = ComponentAccessor.getUserManager().getUserByKey(key);
		} else {
			this.user = jiraAuthenticationContext.getLoggedInUser();
		}
		AbsenceHistory.getHistory(user, this);
		return SUCCESS;
	}

	@Override
	public String doDefault() throws Exception {
		if (userKey != null && !userKey.isEmpty()) {
			user = ComponentAccessor.getUserManager().getUserByKey(userKey);
		}
		if (user == null) {
			user = jiraAuthenticationContext.getLoggedInUser();
		}
		return INPUT;
	}

	@Override
	public List<Absence> filter(List<Issue> issues) {
		List<Absence> absences = new ArrayList<>();
		vacations = new ArrayList<>();
		sicknesses = new ArrayList<>();
		sum = 0;
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int year = cal.get(Calendar.YEAR);
		cal.set(year, 0, 1);
		Date startOfYear = cal.getTime();
		for (Issue issue : issues) {
			try {
				Absence absence = Absence.newInstance(issue);
				if (startOfYear.compareTo(absence.getEndDate()) > 0) {
					log.debug("Not adding entry {} > {}", startOfYear, absence.getEndDate());
					continue;
				} else if (startOfYear.compareTo(absence.getStartDate()) > 0) {
					absence.setStartDate(startOfYear);
					Timespan timespan = new Timespan(getUser(), startOfYear, absence.getEndDate());
					double numWorkingDays = timespan.getNumberOfWorkingDays();
					log.debug("Setting numberOfWorkingDays {}", numWorkingDays);
					absence.setNumberOfWorkingDays((absence.isHalfDay() ? 0.5 : 1.0) * numWorkingDays);
				}
				absences.add(absence);
				if (absence instanceof Sickness) {
					sicknesses.add((Sickness) absence);
				} else if (absence instanceof Vacation) {
					Vacation vacation = (Vacation) absence;
					sum += vacation.getNumberOfWorkingDays();
					vacations.add(vacation);
				}
			} catch (Exception e) {
				log.error("Unable to get entry for issue {}, {}", issue, e.getMessage());
			}
		}
		return absences;
	}

	@SuppressWarnings("deprecation")
	@Override
	public Query getJqlQuery() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.set(cal.get(Calendar.YEAR), 0, 1);
		Date startOfYear = cal.getTime();
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		CustomField cfEnd = cfm.getCustomFieldObjectByName("Finish");
		JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
		builder.where().issueType("Urlaubsantrag", "Sickness").and().reporterIsCurrentUser().and()
				.status("Approved", "Closed").and().customField(cfEnd.getIdAsLong()).gtEq(df.format(startOfYear))
				.endWhere().orderBy().addSortForFieldName("Start", SortOrder.ASC, true);
		Query query = builder.buildQuery();
		return query;
	}

}
