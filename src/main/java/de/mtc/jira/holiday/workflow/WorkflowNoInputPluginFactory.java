package de.mtc.jira.holiday.workflow;

import java.util.HashMap;
import java.util.Map;

import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;

public class WorkflowNoInputPluginFactory extends AbstractWorkflowPluginFactory
		implements WorkflowPluginFunctionFactory {

	private final static String USER_NAME = "user";
	
	@Override
	public Map<String, ?> getDescriptorParams(Map<String, Object> arg0) {
		return new HashMap<String, Object>();
	}

	@Override
	public Map<String, Object> getVelocityParams(String arg0, AbstractDescriptor arg1) {
		return new HashMap<String, Object>();
	}

	@Override
	protected void getVelocityParamsForEdit(Map<String, Object> velocityParams, AbstractDescriptor descriptor) {
		velocityParams.put(USER_NAME, descriptor);
	}

	@Override
	protected void getVelocityParamsForInput(Map<String, Object> velocityParams) {
		velocityParams.put(USER_NAME, "stepanie.lerch");
	}

	@Override
	protected void getVelocityParamsForView(Map<String, Object> velocityParams, AbstractDescriptor descriptor) {
		velocityParams.put(USER_NAME, descriptor);
	}
}
