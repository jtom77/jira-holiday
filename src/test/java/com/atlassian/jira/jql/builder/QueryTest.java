package com.atlassian.jira.jql.builder;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.atlassian.jira.jql.parser.DefaultJqlQueryParser;
import com.atlassian.jira.jql.util.JqlStringSupport;
import com.atlassian.jira.jql.util.JqlStringSupportImpl;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.query.Query;
import com.atlassian.query.QueryImpl;
import com.atlassian.query.order.SortOrder;

import de.mtc.jira.holiday.AbsenceUtil;

public class QueryTest {

	private JqlQueryBuilder queryBuilder;

	private ApplicationUser user;

	private JqlClauseBuilder clauseBuilder;
	private JqlOrderByBuilder orderByBuilder;
	private JqlStringSupport stringSupport;
	private Long startId = 10L;
	Long endId = 11L;

	private ApplicationUser getUser() {
		return user;
	}
	
	private Query getQuery(JqlClauseBuilder clauseBuilder, JqlOrderByBuilder orderByBuilder) {
		Query result = new QueryImpl(clauseBuilder.buildClause(), orderByBuilder.buildOrderBy(), null);
		return result;
	}

	@Before
	public void setUp() throws Exception {
		clauseBuilder = new DefaultJqlClauseBuilder((TimeZoneManager) null);
		orderByBuilder = new JqlOrderByBuilder(null) {
			@Override
			public JqlOrderByBuilder addSortForFieldName(String fieldName, SortOrder order, boolean makePrimarySort) {
				return add(fieldName, order, makePrimarySort);
			}
		};
		orderByBuilder.addSortForFieldName("FIELD", SortOrder.ASC, true);
		stringSupport = new JqlStringSupportImpl(new DefaultJqlQueryParser());
		queryBuilder = Mockito.mock(JqlQueryBuilder.class);
		Mockito.when(queryBuilder.where()).thenReturn(clauseBuilder);
		Mockito.when(queryBuilder.buildQuery())
				.thenReturn(getQuery(clauseBuilder, orderByBuilder));
		
		user = Mockito.mock(ApplicationUser.class);
		Mockito.when(user.getName()).thenReturn("jesus.christ");
		Mockito.when(user.getKey()).thenReturn("jesus.christ");
	}

	@Test
	public void test2() {

		System.out.println("Running test 2");
		
		String issueType = "Urlaubsantrag";
		Date startDate = new Date();
		Date endDate = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

		Query query = queryBuilder.where().issueType(issueType).and().reporterIsCurrentUser().and().status()
				.notIn("Rejected", "Closed").and().sub().sub().customField(startId).gtEq(dateFormat.format(startDate))
				.and().customField(startId).ltEq(dateFormat.format(endDate)).endsub().or().sub().customField(endId)
				.gtEq(dateFormat.format(startDate)).and().customField(endId).ltEq(dateFormat.format(endDate)).endsub()
				.endsub().buildQuery();

		System.out.println("=========> " + stringSupport.generateJqlString(query));

	}

	@Test
	public void test3() {
		Query query = queryBuilder.where().issueType("Urlaubsantrag").and().reporter()
				.in(getUser().getKey(), getUser().getName()).and().customField(endId)
				.gtEq(AbsenceUtil.formatDate(AbsenceUtil.startOfYear())).buildQuery();
				
		System.out.println("=========> " + stringSupport.generateJqlString(query));
	}
}
