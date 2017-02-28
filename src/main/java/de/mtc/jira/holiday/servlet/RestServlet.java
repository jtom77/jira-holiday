package de.mtc.jira.holiday.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class RestServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		System.out.println("Running");
		
		resp.setContentType("application/json; charset=UTF-8");
		PrintWriter out = resp.getWriter();

		out.println("Context: " + req.getContextPath());
		
		testIt(out);	
		
	}
	
	public static void testIt(PrintWriter out) throws IOException {
		Client client = Client.create();

		String suffix = "/rest/api/2/field";
		
		try {
			String url = ComponentAccessor.getComponent(JiraBaseUrls.class).baseUrl();
			out.println("Base URL: " + url);
			String request = url+suffix;
			out.println("Request: " + request);
		} catch(Exception e) {
			out.println("Error: " + e.getMessage());
		}

		WebResource webResource = client.resource("https://jira.mtc.berlin/rest/api/2/field");
		ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);
		
		if (response.getStatus() != 200) {
			out.println("<p>Failed : HTTP error code : " + response.getStatus() + "</p>");
			throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
		}

		String output = response.getEntity(String.class);
		out.println("<p>Output from Server .... </p>");
		out.println("<p>" + output + "</p>");
	}
	
}
