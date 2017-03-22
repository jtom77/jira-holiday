package de.mtc.jira.absence.webwork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.web.action.JiraWebActionSupport;

public class ProjectConfigurator extends JiraWebActionSupport {

	private static final long serialVersionUID = 1L;

	private final static Logger log = LoggerFactory.getLogger(ProjectConfigurator.class);
	
	private String xmlConfig;	
	private String wfConfig0;
	private String wfConfig1;
	private String wfConfig2;
	
	public void setWfConfig0(String wfConfig0) {
		log.debug("wfconfig0={}", wfConfig0);
		this.wfConfig0 = wfConfig0;
	}
	
	public void setWfConfig1(String wfConfig1) {
		log.debug("wfconfig1={}", wfConfig1);
		this.wfConfig1 = wfConfig1;
	}
	
	public void setWfConfig2(String wfConfig2) {
		log.debug("wfconfig2={}", wfConfig2);
		this.wfConfig2 = wfConfig2;
	}

	public void setXmlConfig(String xmlConfig) {
		log.debug("xmlConfig={}", xmlConfig);
		this.xmlConfig = xmlConfig;
	}
	
	public String getXmlConfig() {
		return xmlConfig;
	}
	
	public String getWfConfig0() {
		return wfConfig0;
	}
	
	public String getWfConfig1() {
		return wfConfig1;
	}
	
	public String getWfConfig2() {
		return wfConfig2;
	}
	
	@Override
	protected String doExecute() throws Exception {
		return SUCCESS;
	}
	
	@Override
	public String doDefault() throws Exception {
		return INPUT;
	}
}
