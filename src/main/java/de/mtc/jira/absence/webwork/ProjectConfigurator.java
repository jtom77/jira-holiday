package de.mtc.jira.absence.webwork;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.module.propertyset.PropertySetManager;

import webwork.action.ServletActionContext;
import webwork.multipart.MultiPartRequestWrapper;

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
		MultiPartRequestWrapper wrapper = ServletActionContext.getMultiPartRequest();
		File file = wrapper.getFile("fileField");
		if (file != null) {
			StringBuilder buf = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
				String line = null;
				while ((line = reader.readLine()) != null) {
					buf.append(line);
				}
			}
			storeProperty(buf.toString());
			getProperty();
		}
		return SUCCESS;
	}

	private void storeProperty(String xml) {
		Map<String, Object> entityDetails = new HashMap<String, Object>();
		entityDetails.put("delegator.name", "default");
		entityDetails.put("entityName", "jira.properties");
		entityDetails.put("entityId", new Long(1));
		PropertySet props = PropertySetManager.getInstance("ofbiz", entityDetails);
		System.out.println("Storing description " + xml);
		props.setText("configtest", xml);
	}

	private void getProperty() {
		Map<String, Object> entityDetails = new HashMap<String, Object>();
		entityDetails.put("delegator.name", "default");
		entityDetails.put("entityName", "jira.properties");
		entityDetails.put("entityId", new Long(1));
		PropertySet props = PropertySetManager.getInstance("ofbiz", entityDetails);
		System.out.println("GET:: " + props.getText("configtest"));
	}

	@Override
	public String doDefault() throws Exception {
		return INPUT;
	}
}
