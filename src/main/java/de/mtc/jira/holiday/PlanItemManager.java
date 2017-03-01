package de.mtc.jira.holiday;

import java.util.HashMap;
import java.util.Map;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class PlanItemManager {

	private final static String ALLOC_ITEM_PATH = "rest/tempo-planning/1/allocation";

	private Boolean isHalfDay = false;
	private Integer commitment = 100;
	private String start;
	private String finish;
	private Issue issue;
	private ApplicationUser user;

	public PlanItemManager(Issue issue) {
		this.issue = issue;
		this.user = issue.getReporter();
	}

	public PlanItemManager(Issue issue, ApplicationUser user) {
		this.issue = issue;
		this.user = user;
	}

	public void setStart(String start) {
		this.start = start;
	}

	public void setFinish(String finish) {
		this.finish = finish;
	}

	public ClientResponse createPlanItem() {
		return new JiraRestClient().post(ALLOC_ITEM_PATH, new JSONObject(getDataMap()).toString());
	}

	public ClientResponse getPlanningItems() {
		String uri = ALLOC_ITEM_PATH + "?assigneeKeys=" + user.getKey() + "&startDate=" + start + "&endDate=" + finish;
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
		json.put("end", finish);

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
