package de.mtc.jira.holiday;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class WorkflowHelperTest {

	@Test
	public void testProperty() {
		
		Assert.assertEquals("rest/api/2/field", WorkflowHelper.getProperty("rest.api.allfields"));
		
		Assert.assertEquals("Start Date", WorkflowHelper.getProperty("cf.start_date"));
		Assert.assertEquals("End Date", WorkflowHelper.getProperty("cf.end_date"));
		Assert.assertEquals("Jahresurlaub", WorkflowHelper.getProperty("cf.annual_leave"));
		Assert.assertEquals("Resturlaub", WorkflowHelper.getProperty("cf.residual_days"));

		
	}
	
	@Test
	public void testTemplate() {
		
		String template = "abc{one}def{two}ghi{three}jk";
		String expected = "abc1def2ghi3jk";
		
		Map<String,String> replacements = new HashMap<>();
		replacements.put("one", "1");
		replacements.put("two", "2");
		replacements.put("three", "3");
		Assert.assertEquals(expected, WorkflowHelper.processTemplate(template, replacements));
		
		
		expected ="rest/tempo-core/1/user/schedule/?user=admin&from=2017-01-01&to=2017-07-07";
		replacements = new HashMap<>();
		replacements.put("user", "admin");
		replacements.put("start", "2017-01-01");
		replacements.put("end", "2017-07-07");
		Assert.assertEquals(expected, WorkflowHelper.getProperty("rest.api.workingdays", replacements));
		
	}
	
	
}
