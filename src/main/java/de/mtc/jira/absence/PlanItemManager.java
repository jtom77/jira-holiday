package de.mtc.jira.absence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

public class PlanItemManager {
	
	private final static Logger log = LoggerFactory.getLogger(PlanItemManager.class);

	private Integer commitment = 100;
	private String start;
	private String end;
	private Issue issue;
	private ApplicationUser user;
	private JiraRestClient jiraRestClient;

	public PlanItemManager(Issue issue, Integer commitment, JiraRestClient jiraRestClient) {
		if (issue == null) {
			throw new IllegalArgumentException("Issue cannot be null");
		}
		this.issue = issue;
		this.user = issue.getReporter();
		this.commitment = commitment;
		this.jiraRestClient = jiraRestClient;
	}
	
	public PlanItemManager(Issue issue, Integer commitment) {
		this(issue, commitment, new JiraRestClient());
	}

	public void setTimespan(String start, String end) {
		this.start = start;
		this.end = end;
	}

	public ClientResponse createPlanItem() {
		String uri = ConfigMap.get("rest.api.planningitems.create");
		return jiraRestClient.post(uri, new JSONObject(getDataMap()).toString());
	}
		

	public String getUniquePlanningItemToDelete() throws JiraValidationException {
		Map<String, String> replacements = new HashMap<>();
		replacements.put("user", user.getKey());
		replacements.put("start", start);
		replacements.put("end", end);
		String request = ConfigMap.get("rest.api.planningitems.getByAssignee", replacements);
		String response = jiraRestClient.get(request).getEntity(String.class);
		Set<String> ids = new HashSet<>();
		try {
			JSONArray planItems = new JSONArray(response);
			int length = planItems.length();
			for (int index = 0; index < length; index++) {
				JSONObject parent = planItems.getJSONObject(index);
				JSONObject planItem = parent.getJSONObject("planItem");
				String planItemKey = null;
				try {
					planItemKey = planItem.getString("key");
				} catch(JSONException ex) {
					// just wait, there's a chance we will find it anyway
					continue;
				}
				//String start = (String) planItem.get("start");
				//String end = (String) planItem.get("end");
				if(issue.getKey().equals(planItemKey)) {
					ids.add(String.valueOf(parent.getInt("id")));
				}
			}
		} catch (JSONException e) {
			throw new JiraValidationException("Unable to delete planning item for issue " + issue.getKey(), e);
		}
		if(ids.size() < 1) {
			throw new JiraValidationException("No planning items were found for issue " + issue);
		} else if(ids.size() > 1) {
			log.error("Duplicate planning items were found for issue {} IDs: [{}]", issue, ids);
			//throw new JiraValidationException("Duplicate planning items were found for issue " + issue + " " + ids);
		}
		return ids.iterator().next();
	}
	
	
	public ClientResponse deletePlanItems() throws JiraValidationException {
		String id = getUniquePlanningItemToDelete();
		Map<String, String> repl = new HashMap<>();
		repl.put("id", id);
		String req = ConfigMap.get("rest.api.planningitems.delete", repl);
		return jiraRestClient.delete(req);
	}
	
	
	public ClientResponse getPlanningItems() {
		Map<String, String> replacements = new HashMap<>(4);
		replacements.put("user", user.getKey());
		// TODO Is it really the user key?
		replacements.put("start", start);
		replacements.put("end", end);
		String uri = ConfigMap.get("rest.api.planningitems.get", replacements);
		return jiraRestClient.get(uri);
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
