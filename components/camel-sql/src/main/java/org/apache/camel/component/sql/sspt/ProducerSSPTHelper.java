package org.apache.camel.component.sql.sspt;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.sql.sspt.ast.ParseException;

import javax.sql.DataSource;

/**
 * Created by snurmine on 1/1/16.
 */
public class ProducerSSPTHelper {

    public static final String SSPT_QUERY_PREFIX = "sspt:";
    final DataSource dataSource;
    SimpleStoredProcedureFactory simpleStoredProcedureFactory = new SimpleStoredProcedureFactory();

    public ProducerSSPTHelper(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void handleSstpQuery(String sql, Exchange exchange) {
        try {
            String sstp = sql.substring(SSPT_QUERY_PREFIX.length());
            //TODO: cache parsed SimpleStoredProcedure to LRU-cache.
            SimpleStoredProcedure storedProcedure = simpleStoredProcedureFactory.createFromString(sstp, dataSource);
            storedProcedure.execute(exchange);
        } catch (ParseException parseException) {
            throw new RuntimeCamelException(parseException);
        }
    }

    public boolean isSSPTQuery(String sql) {
        return sql.startsWith(SSPT_QUERY_PREFIX);
    }
}
