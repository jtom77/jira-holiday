package de.mtc.jira.holiday;

import java.util.HashMap;
import java.util.Map;

import org.jfree.util.Log;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class PlanItemManager {

	private Integer commitment = 100;
	private String start;
	private String end;
	private Issue issue;
	private ApplicationUser user;

	public PlanItemManager(Issue issue) {
		if (issue == null) {
			throw new IllegalArgumentException("Issue cannot be null");
		}
		this.issue = issue;
		this.user = issue.getReporter();
	}

	public PlanItemManager(Issue issue, ApplicationUser user) {
		this.issue = issue;
		this.user = user;
	}

	public void setTimespan(String start, String end) {
		this.start = start;
		this.end = end;
	}

	public ClientResponse createPlanItem() {
		String uri = WorkflowHelper.getProperty("rest.api.planningitems.create");
		return new JiraRestClient().post(uri, new JSONObject(getDataMap()).toString());
	}

	public void deletePlanItems() {
		Map<String, String> replacements = new HashMap<>();
		replacements.put("user", user.getKey());
		String request = WorkflowHelper.getProperty("rest.api.planningitems.getByReporter", replacements);
		JiraRestClient restClient = new JiraRestClient();
		String response = restClient.get(request).getEntity(String.class);
		try {
			JSONArray planItems = new JSONArray(response);
			int length = planItems.length();
			for(int i=0; i<length; i++) {
				JSONObject parent = planItems.getJSONObject(i);
				JSONObject planItem = parent.getJSONObject("planItem");
				if(issue.getKey().equals(planItem.getString("key"))) {
					Map<String, String> repl = new HashMap<>();
					repl.put("id", String.valueOf(parent.getInt("id")));
					String req = WorkflowHelper.getProperty("rest.api.planningitems.delete", repl);
					restClient.delete(req);
				}
			}
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		}
	}

	public ClientResponse getPlanningItems() {
		Map<String, String> replacements = new HashMap<>(4);
		replacements.put("user", user.getKey()); // TODO Is it really the user
													// key?
		replacements.put("start", start);
		replacements.put("end", end);
		String uri = WorkflowHelper.getProperty("rest.api.planningitems.get", replacements);
		return new JiraRestClient().get(uri);
	}

	private Map<String, Object> getDataMap() {

		Map<String, Object> json = new HashMap<>();
		Map<String, Object> val;

		val = new HashMap<>();
		json.put("planItem", val);
		val.put("id", issue.getId());
		val.put("type", "ISSUE");

		val = new HashMap<>();
		json.put("scope", val);
		val.put("id", issue.getProjectId());
		val.put("type", "project");

		val = new HashMap<>();
		json.put("assignee", val);
		val.put("key", user.getKey());
		val.put("type", "user");

		json.put("commitment", commitment);
		json.put("start", start);
		json.put("end", end);

		val = new HashMap<>();
		json.put("recurrence", val);
		val.put("rule", "NEVER");

		return json;

	}

	public static void main(String[] args) {

		try {

			Client client = Client.create();

			WebResource webResource = client.resource("https://jira.mtc.berlin/rest/api/2/field");
			ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);

			if (response.getStatus() != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
			}

			String output = response.getEntity(String.class);

			System.out.println("Output from Server .... \n");
			System.out.println(output);

		} catch (Exception e) {

			e.printStackTrace();

		}

	}

}
