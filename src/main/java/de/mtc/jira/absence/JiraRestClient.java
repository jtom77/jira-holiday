package de.mtc.jira.absence;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.jira.user.ApplicationUser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

import webwork.action.ActionContext;

public class JiraRestClient {

	private final static Logger log = LoggerFactory.getLogger(JiraRestClient.class);

	private String acceptMimeType = "application/json";
	private String baseUrl;
	private ApplicationUser user;

	private Client client;

	private String sessionId;
	
	private static final String COOKIE = "Cookie";

	public JiraRestClient() {
		this.baseUrl = ComponentAccessor.getComponent(JiraBaseUrls.class).baseUrl();
		this.user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
		this.client = Client.create();

		@SuppressWarnings("rawtypes")
		Map sessionMap = ActionContext.getSession();
		this.sessionId = "JSESSIONID=" + ((String) sessionMap.get("ASESSIONID")).replaceAll("[0-9a-z]*-", "");
	}

	public JiraRestClient(String baseUrl, ApplicationUser user) {
		this.baseUrl = baseUrl;
		this.user = user;
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

		log.debug("POST: user: {}, req: {}, Cookie={}, Payload: {}", user, requestURI, sessionId, json);
		WebResource webResource = client.resource(requestURI);
		ClientResponse response = webResource.header(COOKIE, sessionId).type("application/json")
				.post(ClientResponse.class, json);


		log.debug("Response: {}", response);

		return response;
	}

	public ClientResponse get(String relativeURI) {
		String requestURI = buildRequestUri(relativeURI);

		WebResource webResource = client.resource(requestURI);

		log.debug("GET: user: {}, req: {}, Cookie={}", user, requestURI, sessionId);
		ClientResponse response = webResource.header(COOKIE, sessionId).accept(acceptMimeType)
				.get(ClientResponse.class);

		log.debug("Response: {}", response);

		return response;
	}

	public ClientResponse delete(String relativeURI) {
		String requestURI = buildRequestUri(relativeURI);

		WebResource webResource = client.resource(requestURI);

		log.debug("DELETE: user: {}, req: {}, Cookie={}", user, requestURI, sessionId);
		ClientResponse response = webResource.header(COOKIE, sessionId).delete(ClientResponse.class);

		log.debug("Response: {}", response);

		return response;
	}

	private String buildRequestUri(String tail) {
		return baseUrl + "/" + tail;
	}

	public static void main(String[] args) {
		String req = "http://localhost:2990/jira/rest/tempo-core/1/user/schedule/?user=admin&from=2017-03-01&to=2017-03-13";
		Client client = Client.create();
		WebResource webResource = client.resource(req);
		ClientResponse response = webResource.header("Cookie","JSESSIONID=CB938C3BA5921FF69DB0E503659E1807").get(ClientResponse.class);
																
		String result = response.getEntity(String.class);
	
		System.out.println(response);
		System.out.println(result);
	}
}
