package de.mtc.jira.holiday;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.sun.jersey.api.client.ClientResponse;

import webwork.action.ActionContext;

public class RestApiTester extends JiraWebActionSupport {
	
	private static final long serialVersionUID = 1L;
	private final static Logger log = LoggerFactory.getLogger(RestApiTester.class);
	
	private String apiUser;
	private String restCall;
	private String apiPassword;
	private String payload;
	private String response;
	
	public void setApiUser(String user) {
		log.debug("Setting user={}", user);
		this.apiUser = user;
	}
	
	public void setRestCall(String restCall) {
		log.debug("Setting restCall={}", restCall);
		this.restCall = restCall;
	}

	public void setPayload(String payload) {
		log.debug("Setting payload={}", payload);
		this.payload = payload;
	}
	
	public void setApiPassword(String password) {
		log.debug("Setting password={}", password);
		this.apiPassword = password;
	}
	
	public String getResponse() {
		return response;
	}

	
	@SuppressWarnings("rawtypes")
	public Map getSession() {
		return ActionContext.getSession();
	}
	
	
	@Override
	protected String doExecute() throws Exception {
		log.debug("Executing main method");
		JiraRestClient restClient = new JiraRestClient();
		if(apiUser != null && !apiUser.isEmpty() && apiPassword != null && !apiPassword.isEmpty()) {
			restClient.authenticate(apiUser, apiPassword);
		}
		log.debug("Making rest call {}", restCall);
		ClientResponse clientResponse;
		if(payload != null && !payload.isEmpty()) {
			clientResponse = restClient.post(restCall, payload);
		} else {
			clientResponse = restClient.get(restCall);
		}
		this.response = clientResponse.getEntity(String.class);
		return SUCCESS;
	}
	
	@Override
	public String doDefault() throws Exception {
		log.debug("Executing default method");
		return INPUT;
	}

	
}
