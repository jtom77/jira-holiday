package de.mtc.jira.holiday;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class JiraRestClient {

	private String acceptMimeType = "application/json";
	
	public JiraRestClient() {

	}

	public void setAcceptMimeType(String acceptMimeType) {
		this.acceptMimeType = acceptMimeType;
	}
	
	public ClientResponse execute(String requestURI) {
		String baseUrl = ComponentAccessor.getComponent(JiraBaseUrls.class).baseUrl();
		String uri = baseUrl + "/" + requestURI;
		Client client = Client.create();
		WebResource webResource = client.resource(uri);
		ClientResponse response = webResource.accept(acceptMimeType).get(ClientResponse.class);
		return response;
	}
}
