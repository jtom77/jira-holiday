package de.mtc.jira.absence.workflow;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginValidatorFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;

public class ValidatorPluginFactory extends AbstractWorkflowPluginFactory implements WorkflowPluginValidatorFactory {

	private static final Logger log = LoggerFactory.getLogger(ValidatorPluginFactory.class);
	
	@Override
	public Map<String, ?> getDescriptorParams(Map<String, Object> arg0) {
		log.debug("Get Descriptor params");
		return new HashMap<String, Object>();
	}

	@Override
	protected void getVelocityParamsForEdit(Map<String, Object> arg0, AbstractDescriptor arg1) {
		log.debug("Get params for edit");
	}

	@Override
	protected void getVelocityParamsForInput(Map<String, Object> arg0) {
		log.debug("Get params for input");
	}

	@Override
	protected void getVelocityParamsForView(Map<String, Object> arg0, AbstractDescriptor arg1) {
		log.debug("Get params for view");
	}

}
