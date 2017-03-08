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
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

public class PlanItemManager {

	private Integer commitment = 100;
	private String start;
	private String end;
	private Issue issue;
	private ApplicationUser user;

	public PlanItemManager(Issue issue, Integer commitment) {
		if (issue == null) {
			throw new IllegalArgumentException("Issue cannot be null");
		}
		this.issue = issue;
		this.user = issue.getReporter();
		this.commitment = commitment;
	}

	public void setTimespan(String start, String end) {
		this.start = start;
		this.end = end;
	}

	public ClientResponse createPlanItem() {
		String uri = ConfigMap.get("rest.api.planningitems.create");
		return new JiraRestClient().post(uri, new JSONObject(getDataMap()).toString());
	}

	public void deletePlanItems() {
		Map<String, String> replacements = new HashMap<>();
		replacements.put("user", user.getKey());
		String request = ConfigMap.get("rest.api.planningitems.getByReporter", replacements);
		JiraRestClient restClient = new JiraRestClient();
		String response = restClient.get(request).getEntity(String.class);
		try {
			JSONArray planItems = new JSONArray(response);
			int length = planItems.length();
			for (int i = 0; i < length; i++) {
				JSONObject parent = planItems.getJSONObject(i);
				JSONObject planItem = parent.getJSONObject("planItem");
				if (issue.getKey().equals(planItem.getString("key"))) {
					Map<String, String> repl = new HashMap<>();
					repl.put("id", String.valueOf(parent.getInt("id")));
					String req = ConfigMap.get("rest.api.planningitems.delete", repl);
					restClient.delete(req);
				}
			}
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		}
	}

	public ClientResponse getPlanningItems() {
		Map<String, String> replacements = new HashMap<>(4);
		replacements.put("user", user.getKey());
		// TODO Is it really the user key?
		replacements.put("start", start);
		replacements.put("end", end);
		String uri = ConfigMap.get("rest.api.planningitems.get", replacements);
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
		
		System.out.println("Running");

		try {

			Client client = Client.create();
			client.addFilter(new HTTPBasicAuthFilter("admin","admin"));
			
			String json = "{\"recurrence\":{\"rule\":\"NEVER\"},\"planItem\":{\"id\":10103,\"type\":\"ISSUE\"},\"scope\":{\"id\":10000,\"type\":\"project\"},\"start\":\"2017-03-06\",\"commitment\":100,\"end\":\"2017-03-17\",\"assignee\":{\"type\":\"user\",\"key\":\"admin\"}}";
			String uri = "http://localhost:2990/jira/rest/tempo-planning/1/allocation";
						

			WebResource webResource = client.resource(uri);
			ClientResponse response = webResource.type("application/json")
					.post(ClientResponse.class, json);

			String output = response.getEntity(String.class);

			System.out.println("Output from Server .... \n");
			System.out.println(output);

		} catch (Exception e) {

			e.printStackTrace();

		}

	}

}
