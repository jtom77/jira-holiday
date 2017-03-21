package ut.de.mtc.jira;

import static org.junit.Assert.assertEquals;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

import de.mtc.jira.api.MyPluginComponent;
import de.mtc.jira.holiday.JiraRestClient;
import de.mtc.jira.holiday.JiraValidationException;
import de.mtc.jira.holiday.PlanItemManager;
import de.mtc.jira.holiday.Timespan;
import de.mtc.jira.impl.MyPluginComponentImpl;


public class RestCallTest {
	
	private List<ApplicationUser> testUsers;
	private final static String START = "2017-01-01";
	private final static String END = "2017-05-01";
	private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
	private final static String BASE_URL = "https://jira-dev.mtc.berlin";
	
	private final static String USER = "thomas.epp";
	private final static String PASSWORD = "hilftjanix";


	@Before
	public void init() throws JSONException {
		testUsers = new ArrayList<>();
		Client client = Client.create();
		client.addFilter(new HTTPBasicAuthFilter(USER, PASSWORD));
		String req = "https://jira-dev.mtc.berlin/rest/api/2/user/search?startAt=0&maxResults=1000&username=%25";
		WebResource webResource = client.resource(req);
		ClientResponse response = webResource.get(ClientResponse.class);
		String json = response.getEntity(String.class);
		JSONArray array = new JSONArray(json);
		for (int index = 0; index < array.length(); index++) {
			JSONObject user = (JSONObject) array.get(index);
			String name = user.getString("name");
			String key = user.getString("key");
			if (!name.equals(key)) {
				testUsers.add(mockUser(user));
			}
		}
	}

	private ApplicationUser mockUser(JSONObject user) throws JSONException {
		ApplicationUser mock = Mockito.mock(ApplicationUser.class);
		Mockito.when(mock.getName()).thenReturn(user.getString("name"));
		Mockito.when(mock.getKey()).thenReturn(user.getString("key"));
		return mock;
	}
	
	//@Test
	public void testMyName() {
		MyPluginComponent component = new MyPluginComponentImpl(null);
		assertEquals("names do not match!", "myComponent", component.getName());
	}
	
	@Test
	public void testProperties() {
		for(ApplicationUser user : testUsers) {
			JiraRestClient restClient = new JiraRestClient(BASE_URL, user);
			restClient.authenticate(USER, PASSWORD);
			
		}
	}

	@Test
	public void testTimespan() throws JSONException, JiraValidationException, ParseException {
		for (ApplicationUser user : testUsers) {
			JiraRestClient restClient = new JiraRestClient(BASE_URL, user);
			restClient.authenticate(USER, PASSWORD);
			Timespan timespan = new Timespan(user, df.parse(START), df.parse(END), restClient);
			Assert.assertTrue(timespan.getNumberOfWorkingDays() > 5);
			JSONArray allDays = timespan.getWorkingDays();
			List<JSONObject> workingDays = new ArrayList<>();
			for(int index = 0; index < allDays.length(); index++) {
				JSONObject day = (JSONObject) allDays.get(index);
				String type = day.getString("type");
				if("WORKING_DAY".equals(type)) {
					workingDays.add(day);
				}
			}
			Assert.assertEquals(timespan.getNumberOfWorkingDays(), workingDays.size());
		}
	}
	

	private void assertAssigneesSet(JSONArray array) throws JSONException {
		
		String assignee = null;
		for(int index = 0; index < array.length(); index++) {
			JSONObject planItem = (JSONObject) array.get(index);
			
			String newAssignee = (String) ((JSONObject)planItem.get("assignee")).get("key");
			if(assignee != null) {
				Assert.assertEquals(assignee, newAssignee);
			}
			assignee = newAssignee;
			
		}

	}
	
	
	@Test
	public void testGetPlanningItems() throws JSONException, JiraValidationException {		
		for (ApplicationUser user : testUsers) {

			JiraRestClient restClient = new JiraRestClient(BASE_URL, user);
			restClient.authenticate(USER, PASSWORD);
			
			Issue issue = Mockito.mock(Issue.class);
			Mockito.when(issue.getId()).thenReturn(44969L);
			Mockito.when(issue.getReporter()).thenReturn(user);
			Mockito.when(issue.getProjectId()).thenReturn(10546L);
			Mockito.when(issue.getKey()).thenReturn("ISFABW-1023");
						
			PlanItemManager planItemManager = new PlanItemManager(issue, 100, restClient);
			planItemManager.setTimespan(START, END);
			
			ClientResponse response = planItemManager.getPlanningItems();
			String source = response.getEntity(String.class);
			JSONArray planItems = new JSONArray(source);
			
			assertAssigneesSet(planItems);
			System.out.println("\n\n Creating and deleting plan Items \n\n");
			
			int lengthBefore = planItems.length();
			
			response = planItemManager.createPlanItem();
			System.out.println(response.getEntity(String.class));
			
			response = planItemManager.getPlanningItems();
			source = response.getEntity(String.class);
			planItems = new JSONArray(source);
			
			int lengthAfterCreate =  planItems.length();
			
			Assert.assertEquals(lengthBefore + 1, lengthAfterCreate);
			
			response = planItemManager.deletePlanItems();
			System.out.println(response.getEntity(String.class));
			
			response = planItemManager.getPlanningItems();
			source = response.getEntity(String.class);
			planItems = new JSONArray(source);
			
			int lengthAfterDelete = planItems.length(); 
			
			Assert.assertEquals(lengthBefore, lengthAfterDelete);
					
			System.out.println(source);
		}
	}
}