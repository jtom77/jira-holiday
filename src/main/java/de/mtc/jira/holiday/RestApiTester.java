package de.mtc.jira.holiday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.sun.jersey.api.client.ClientResponse;

public class RestApiTester extends JiraWebActionSupport {
	
	private static final long serialVersionUID = 1L;
	private final static Logger log = LoggerFactory.getLogger(RestApiTester.class);
	
	private String user;
	private String restCall;
	private String password;
	private String payload;
	private String response;
	
	public void setUser(String user) {
		log.debug("Setting user={}", user);
		this.user = user;
	}
	
	public void setRestCall(String restCall) {
		log.debug("Setting restCall={}", restCall);
		this.restCall = restCall;
	}

	public void setPayload(String payload) {
		log.debug("Setting payload={}", payload);
		this.payload = payload;
	}
	
	public void setPassword(String password) {
		log.debug("Setting password={}", password);
		this.password = password;
	}
	
	public String getResponse() {
		return response;
	}
	
	@Override
	protected String doExecute() throws Exception {
		log.debug("Executing main method");
		JiraRestClient restClient = new JiraRestClient();
		if(user != null && !user.isEmpty() && password != null && !password.isEmpty()) {
			restClient.authenticate(user, password);
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
