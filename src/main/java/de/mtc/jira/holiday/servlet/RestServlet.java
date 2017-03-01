package de.mtc.jira.holiday.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.sun.jersey.api.client.ClientResponse;

import de.mtc.jira.holiday.PlanItemManager;

public class RestServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("application/json; charset=UTF-8");
		PrintWriter out = resp.getWriter();
		out.println("Context: " + req.getContextPath());
		testIt(out);	
	}
	
	public static void testIt(PrintWriter out) throws IOException {
		Issue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey("HOL-7");
		PlanItemManager manager = new PlanItemManager(issue);
		manager.setStart("2017-03-01");
		manager.setFinish("2017-03-15");
		ClientResponse response = manager.getPlanningItems();
		out.println(response.getEntity(String.class));
	}
}
