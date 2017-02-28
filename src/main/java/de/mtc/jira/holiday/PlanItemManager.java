package de.mtc.jira.holiday;

import java.util.HashMap;
import java.util.Map;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;



public class PlanItemManager {
	
	private Boolean isHalfDay = false;
	private Integer commitment = 100;
	private String start;
	private String finish;
	private Issue issue;
	private ApplicationUser user;
	
	public PlanItemManager(Issue issue) {
		this.issue = issue;
		this.user = issue.getReporter();
		
		ComponentAccessor.getCustomFieldManager().getCustomFieldObject("Start");
		
		
		
	}
	
	
	public void createPlanItem(Issue issue) {
		
		
		
	}
	
	
	private Map<String, Object> getDataMap(Issue issue, ApplicationUser user) {
		StringBuilder sb = new StringBuilder("{");
		sb.append("planItem: { id: " + issue.getId() + ", type: ISSUE },");
		sb.append("scope: { id: " + issue.getProjectId() + ", type: project },");
		sb.append("assignee: { key: " + user.getKey() + ", type: user },");
		sb.append("commitment: ");
		Map<String, Object> result = new HashMap<String, Object>();
		Map<String, Object> id;
		
		id = new HashMap<>();
		result.put("planItem", id);
		id.put("id", issue.getId());
		id.put("type", "ISSUE");

		
		id = new HashMap<>();
		result.put("scope", id);
		id.put("id", issue.getProjectId());
		id.put("type", "project");

		id = new HashMap<>();
		result.put("assignee", id);
		id.put("key", user.getKey());
		id.put("type", "user");

		result.put("commitment", commitment);
		result.put("commitment", start);
		result.put("end", finish);
	
		id = new HashMap<>();
		result.put("recurrence", id);
		id.put("rule", "NEVER");
		
		return result;
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
