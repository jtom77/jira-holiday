package de.mtc.jira.holiday;

import org.junit.Test;
import org.mockito.Mockito;

import com.atlassian.jira.jql.builder.JqlQueryBuilder;


public class QueryTest {

	@Test
	public void testIt() {
		final JqlQueryBuilder builder = Mockito.mock(JqlQueryBuilder.class);
		builder.where().project("id");
	}
	
}
