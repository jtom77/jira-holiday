package de.mtc.jira.holiday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.jira.user.ApplicationUser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

public class JiraRestClient {

	private final static Logger log = LoggerFactory.getLogger(JiraRestClient.class);

	private String acceptMimeType = "application/json";
	private String baseUrl;
	private ApplicationUser user;

	private Client client;

	public JiraRestClient() {
		this.baseUrl = ComponentAccessor.getComponent(JiraBaseUrls.class).baseUrl();
		this.user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
		this.client = Client.create();
	}

	public void setAcceptMimeType(String acceptMimeType) {
		this.acceptMimeType = acceptMimeType;
	}

	public void authenticate(String authUser, String password) {
		client.addFilter(new HTTPBasicAuthFilter(authUser, password));
	}

	public ClientResponse post(String relativeURI, String json) {
		String requestURI = buildRequestUri(relativeURI);

		log.debug("POST: user: {}, req: {}, payload: {}", user, requestURI, json);

		WebResource webResource = client.resource(requestURI);
		ClientResponse response = webResource.type("application/json").post(ClientResponse.class, json);

		log.debug("Response: {}", response);

		return response;
	}
	
	public ClientResponse get(String relativeURI) {
		String requestURI = buildRequestUri(relativeURI);

		WebResource webResource = client.resource(requestURI);

		log.debug("GET: user: {}, req: {}", user, requestURI);

		ClientResponse response = webResource.accept(acceptMimeType).get(ClientResponse.class);

		log.debug("Response: {}", response);

		return response;
	}
	
	public ClientResponse delete(String relativeURI) {
		String requestURI = buildRequestUri(relativeURI);

		WebResource webResource = client.resource(requestURI);

		log.debug("DELETE: user: {}, req: {}", user, requestURI);

		ClientResponse response = webResource.delete(ClientResponse.class);

		log.debug("Response: {}", response);

		return response;
	}
	

	private String buildRequestUri(String tail) {
		return baseUrl + "/" + tail;
	}
}
