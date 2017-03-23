package de.mtc.jira.absence;

import static de.mtc.jira.absence.ConfigMap.SUPERVISOR_KEY;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;

public class AbsenceUtil {

	public final static String START_FIELD_NAME = "Start";
	public final static String END_FIELD_NAME = "Finish";
	public final static String VACATION_TYPE_FIELD_NAME = "Urlaubstyp";
	public final static String SICKNESS_TYPE_FIELD_NAME = "Krankheitsgrund";
	public final static String DAYS_FIELD_NAME = ConfigMap.get("cf.days");

	private final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private final static SimpleDateFormat displayFormat = new SimpleDateFormat("dd.MM.yyyy");

	public static ApplicationUser getSupervisor(ApplicationUser user) {
		PropertyHelper props = new PropertyHelper(user);
		String supervisorName = props.exists(SUPERVISOR_KEY) ? String.valueOf(props.get(SUPERVISOR_KEY)) : "teamlead";
		if (supervisorName == null || supervisorName.isEmpty()) {
			supervisorName = "teamlead";
		}
		return ComponentAccessor.getUserManager().getUserByName(supervisorName);
	}

	public static String formattedStartOfYear() {
		return dateFormat.format(startOfYear());
	}

	public static Date startOfYear() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.set(cal.get(Calendar.YEAR), 0, 1, 0, 0);
		return cal.getTime();
	}

	public static Date today() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0);
		return cal.getTime();
	}

	public static Integer getCurrentYear() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		return cal.get(Calendar.YEAR);
	}

	public static CustomField getCustomField(String name) {
		return ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName(name);
	}

	public static Object getCustomFieldValue(String name, Issue issue) {
		return issue.getCustomFieldValue(getCustomField(name));
	}

	public static Date getStart(Issue issue) {
		return (Date) getCustomFieldValue(START_FIELD_NAME, issue);
	}

	public static Date getEnd(Issue issue) {
		return (Date) getCustomFieldValue(END_FIELD_NAME, issue);
	}

	public static String getFormattedStart(Issue issue) {
		return dateFormat.format(getStart(issue));
	}

	public static String getFormattedEnd(Issue issue) {
		return dateFormat.format(getEnd(issue));
	}

	public static String getDisplayStart(Issue issue) {
		return displayFormat.format(getStart(issue));
	}

	public static String getDisplayEnd(Issue issue) {
		return displayFormat.format(getEnd(issue));
	}
	
	public static String formatDate(Date date) {
		return dateFormat.format(date);
	}
	
	public static String getSicknessType(Issue issue) {
		Object type = getCustomFieldValue(SICKNESS_TYPE_FIELD_NAME, issue);
		return type == null ? (String) type : type.toString();
	}
	
	public static String getVacationType(Issue issue) {
		Object type = getCustomFieldValue(VACATION_TYPE_FIELD_NAME, issue);
		return type == null ? (String) type : type.toString();
	}
	
	public static String getNumberOfDays(Issue issue) {
		return (String) getCustomFieldValue(DAYS_FIELD_NAME, issue);
	}
	
	public static Project getProject() {
		String projectKey = ConfigMap.get("absence.project.key");
		return ComponentAccessor.getProjectManager().getProjectByCurrentKey(projectKey);
	}
	
}
