package org.apache.camel.component.google.bigquery.unit.sql;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.QueryResponse;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLConfiguration;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLEndpoint;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLProducer;
import org.apache.camel.test.junit4.CamelTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleBigQuerySQLProducerBaseTest extends CamelTestSupport {
    protected GoogleBigQuerySQLEndpoint endpoint = mock(GoogleBigQuerySQLEndpoint.class);
    protected Bigquery.Jobs mockJobs = mock(Bigquery.Jobs.class);
    protected Bigquery.Jobs.Query mockQuery = mock(Bigquery.Jobs.Query.class);
    protected GoogleBigQuerySQLProducer producer;
    protected String sql;
    protected String projectId = "testProjectId";
    protected GoogleBigQuerySQLConfiguration configuration = new GoogleBigQuerySQLConfiguration();
    protected Bigquery bigquery;

    protected GoogleBigQuerySQLProducer createAndStartProducer() throws Exception {
        configuration.setProjectId(projectId);
        configuration.setQuery(sql);

        GoogleBigQuerySQLProducer sqlProducer = new GoogleBigQuerySQLProducer(bigquery, endpoint, configuration);
        sqlProducer.start();
        return sqlProducer;
    }

    protected void setupBigqueryMock() throws Exception {
        bigquery = mock(Bigquery.class);

        when(bigquery.jobs()).thenReturn(mockJobs);
        when(bigquery.jobs().query(anyString(), any())).thenReturn(mockQuery);

        QueryResponse mockResponse = new QueryResponse()
                .setNumDmlAffectedRows(1L);
        when(mockQuery.execute()).thenReturn(mockResponse);
    }
}
