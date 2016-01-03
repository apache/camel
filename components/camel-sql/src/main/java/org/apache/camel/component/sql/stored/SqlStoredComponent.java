package org.apache.camel.component.sql.stored;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedureFactory;
import org.apache.camel.impl.UriEndpointComponent;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Created by snurmine on 1/3/16.
 */
public class SqlStoredComponent extends UriEndpointComponent {

    private DataSource dataSource;

    public SqlStoredComponent() {
        super(SqlStoredEndpoint.class);
    }



    TemplateStoredProcedureFactory templateStoredProcedureFactory = new TemplateStoredProcedureFactory();

    public SqlStoredComponent(CamelContext context, Class<? extends Endpoint> endpointClass) {
        super(context, endpointClass);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

            return new SqlStoredEndpoint(templateStoredProcedureFactory,dataSource,remaining);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
