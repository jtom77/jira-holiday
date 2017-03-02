package de.mtc.jira.holiday.workflow;

import java.util.HashMap;
import java.util.Map;

import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;

public class WorkflowNoInputPluginFactory extends AbstractWorkflowPluginFactory
		implements WorkflowPluginFunctionFactory {

	@Override
	public Map<String, ?> getDescriptorParams(Map<String, Object> arg0) {
		//return arg0;
		return new HashMap<String, Object>();
	}

	@Override
	public Map<String, Object> getVelocityParams(String arg0, AbstractDescriptor arg1) {
		return new HashMap<String, Object>();
	}

	@Override
	protected void getVelocityParamsForEdit(Map<String, Object> arg0, AbstractDescriptor arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void getVelocityParamsForInput(Map<String, Object> arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void getVelocityParamsForView(Map<String, Object> arg0, AbstractDescriptor arg1) {
		// TODO Auto-generated method stub
		
	}

}
