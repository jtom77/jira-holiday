package de.mtc.jira.holiday;

import org.jfree.util.Log;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.jira.user.ApplicationUser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

public class JiraRestClient {

	private String acceptMimeType = "application/json";
	private String baseUrl;
	private ApplicationUser user;
	
	public JiraRestClient() {
		this.baseUrl = ComponentAccessor.getComponent(JiraBaseUrls.class).baseUrl();
		this.user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
	}

	public void setAcceptMimeType(String acceptMimeType) {
		this.acceptMimeType = acceptMimeType;
	}

	public ClientResponse post(String relativeURI, String json) {
		String requestURI = buildRequestUri(relativeURI);
		Client client = Client.create();
		client.addFilter(new HTTPBasicAuthFilter("admin","admin"));
		System.out.println("Post request: user " + user + " "+ requestURI);
		Log.debug("DEBUGGING");
		WebResource webResource = client.resource(requestURI);
		ClientResponse response = webResource.type("application/json")
		   .post(ClientResponse.class, json);
		return response;
	}

	public ClientResponse get(String relativeURI) {
		String requestURI = buildRequestUri(relativeURI);
		Client client = Client.create();
		client.addFilter(new HTTPBasicAuthFilter("admin","admin"));
		WebResource webResource = client.resource(requestURI);
		System.out.println("GET request: user " + user + " " + requestURI);
		Log.debug("DEBUGGING");
		ClientResponse response = webResource.accept(acceptMimeType).get(ClientResponse.class);
		return response;
	}
	
	private String buildRequestUri(String tail) {
		return baseUrl + "/" + tail;
	}
}
