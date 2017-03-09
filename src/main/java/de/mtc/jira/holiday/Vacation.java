package de.mtc.jira.holiday;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.user.ApplicationUser;

public class Vacation {
	
	private static final Logger log = LoggerFactory.getLogger(Vacation.class);
	public static final String CF_START_DATE, CF_END_DATE, CF_YEARLY_VACATION, CF_REST_VACATION, CF_TYPE;

	@SuppressWarnings("unused")
	private static final String WHOLE_DAY, HALF_DAY;


	static {
		CF_START_DATE = ConfigMap.get("cf.start_date");
		CF_END_DATE = ConfigMap.get("cf.end_date");
		CF_YEARLY_VACATION = ConfigMap.get("cf.annual_leave");
		CF_REST_VACATION = ConfigMap.get("cf.residual_days");

		CF_TYPE = ConfigMap.get("cf.holiday_type");
		WHOLE_DAY = ConfigMap.get("cf.holiday_type.whole");
		HALF_DAY = ConfigMap.get("cf.holiday_type.half");

	}

	
	private Date startDate;
	private Date endDate;
	private Double numberOfWorkingDays;
	private ApplicationUser user;
	private String type;
	boolean isHalfDay;
	private String issueKey;
	private TimeSpan timespan;
	final SimpleDateFormat df = new SimpleDateFormat("dd.MM.yy");
	

	public Vacation(Issue issue) throws JiraValidationException {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		this.user = issue.getReporter();
		//this.issue = issue;
		
		CustomField cfStart = cfm.getCustomFieldObjectByName(CF_START_DATE);
		CustomField cfEnd = cfm.getCustomFieldObjectByName(CF_END_DATE);
		CustomField cfType = cfm.getCustomFieldObjectByName(CF_TYPE);
		
		if(cfStart == null || cfEnd == null || cfType == null) {
			throw new JiraValidationException("Field missing");
		}
		
		this.startDate = (Date) issue.getCustomFieldValue(cfStart);
		this.endDate = (Date) issue.getCustomFieldValue(cfEnd);
		this.type = (String) issue.getCustomFieldValue(cfType).toString();
		this.isHalfDay = type.contains("Halbe");
		this.timespan = new TimeSpan(user, startDate, endDate);
		this.numberOfWorkingDays = Double.valueOf(timespan.getNumberOfWorkingDays());
		if (isHalfDay) {
			this.numberOfWorkingDays = 0.5 * numberOfWorkingDays;
		}
		this.issueKey = issue.getKey();
	}
	
	
	public Vacation(ApplicationUser user, Date start, Date end, boolean isHalfDay, String issueKey) throws JiraValidationException {
		this.user = user;
		this.startDate = start;
		this.endDate = end;
		this.isHalfDay = isHalfDay;
		this.timespan = new TimeSpan(user, start, end);
		this.numberOfWorkingDays = Double.valueOf(timespan.getNumberOfWorkingDays());
		if (isHalfDay) {
			this.numberOfWorkingDays = 0.5 * numberOfWorkingDays;
		}
		this.issueKey = issueKey;
		this.type = isHalfDay ? "Halbe Tage" : "Ganze Tage";
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}
	
	public Date getStartDate() {
		return startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public Double getNumberOfWorkingDays() {
		return numberOfWorkingDays;
	}

	public ApplicationUser getUser() {
		return user;
	}

	public String getType() {
		return type;
	}

	public boolean isHalfDay() {
		return isHalfDay;
	}

	public String getIssueKey() {
		return issueKey;
	}

	public SimpleDateFormat getDf() {
		return df;
	}
	
	public TimeSpan getTimespan() {
		return timespan;
	}
	
	public Map<String, Object> getVelocityContextParams() {
		Class<Vacation> clazz = Vacation.class;
		Map<String, Object> result = new HashMap<>();
		for(Field field : clazz.getDeclaredFields()) {
			try {
				field.setAccessible(true);
				Object value = field.get(this);
				if(value instanceof Date) {
					value = df.format(value);
				}
				result.put(field.getName(), value);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return result;
	}
	

	public String toString() {
		return String.format("|%s|%s|%s|%s|%f|", issueKey, df.format(startDate), df.format(endDate),
				isHalfDay ? "Halbe Tage" : "Ganze Tage", numberOfWorkingDays);
	}
}