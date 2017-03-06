package de.mtc.jira.holiday.webwork;


import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.sun.jersey.api.client.ClientResponse;

import de.mtc.jira.holiday.JiraRestClient;
import webwork.action.ActionContext;

public class RestApiWebWorker extends JiraWebActionSupport {
	
	private static final long serialVersionUID = 1L;
	private final static Logger log = LoggerFactory.getLogger(RestApiWebWorker.class);
	
	private String apiUser;
	private String restCall = "rest/api/2/field";
	private String apiPassword;
	private String payload;
	private String response;
	
	
	public String getRestCall() {
		return restCall;
	}
	
	public void setApiUser(String apiUser) {
		log.debug("Setting user={}", apiUser);
		this.apiUser = apiUser;
	}
	
	public void setRestCall(String restCall) {
		log.debug("Setting restCall={}", restCall);
		this.restCall = restCall;
	}

	public void setPayload(String payload) {
		log.debug("Setting payload={}", payload);
		this.payload = payload;
	}
	
	public void setApiPassword(String apiPassword) {
		log.debug("Setting password={}", apiPassword);
		this.apiPassword = apiPassword;
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


