package de.mtc.jira.holiday;

import static de.mtc.jira.holiday.ConfigMap.SUPERVISOR_KEY;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;

public class AbsenceUtil {
		
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
	
}
