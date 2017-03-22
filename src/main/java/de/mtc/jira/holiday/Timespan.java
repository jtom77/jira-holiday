package de.mtc.jira.holiday;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.sun.jersey.api.client.ClientResponse;

public class Timespan {
	
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private JSONObject json;
	
	
	public Timespan(ApplicationUser user, Date start, Date end) throws JiraValidationException{
		this(user, start, end, new JiraRestClient());
	}
	
	public Timespan(ApplicationUser user, Date start, Date end, JiraRestClient client) throws JiraValidationException {
		Map<String, String> replacements = new HashMap<>();
		replacements.put("user", user.getName());
		replacements.put("start", dateFormat.format(start));
		replacements.put("end", dateFormat.format(end));
		String req = ConfigMap.get("rest.api.workingdays", replacements);
		ClientResponse response = client.get(req);
		if (!(response.getStatus() == ClientResponse.Status.OK.getStatusCode())) {
			throw new JiraValidationException("Request " + req + " failed: " + response);
		}
		String json = response.getEntity(String.class);
		try {
			this.json = new JSONObject(json);
		} catch (Exception e) {
			throw new JiraValidationException(
					"Unable to parse response for request " + req + ".Server response: " + json, e);
		}
	}
	
	public int getNumberOfWorkingDays() throws JiraValidationException {
		try {
			return json.getInt("numberOfWorkingDays");
		} catch (Exception e) {
			throw new JiraValidationException("Unexpected JSON format", e);
		}
	}


	public JSONArray getWorkingDays() throws JiraValidationException {
		try {
			return json.getJSONArray("days");
		} catch (JSONException e) {
			throw new JiraValidationException("Unexpected JSON format", e);
		}
	}	
}
