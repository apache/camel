package org.apache.camel.component.sql;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.springframework.jdbc.core.JdbcTemplate;

public class SqlProducer extends DefaultProducer<DefaultExchange> {

	private String query;

	private JdbcTemplate jdbcTemplate;

	public SqlProducer(SqlEndpoint endpoint, String query,
			JdbcTemplate jdbcTemplate) {
		super(endpoint);
		this.jdbcTemplate = jdbcTemplate;
		this.query = query;
	}

	public void process(Exchange exchange) throws Exception {
		List<Object> arguments = new ArrayList<Object>();
		for (Iterator<?> i = exchange.getIn().getBody(Iterator.class);
				i.hasNext();) {
			arguments.add(i.next());
		}

		List result = jdbcTemplate.queryForList(query, arguments.toArray());
		exchange.getOut().setBody(result);
	}

}
