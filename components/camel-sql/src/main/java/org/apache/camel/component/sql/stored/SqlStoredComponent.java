package org.apache.camel.component.sql.stored;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedureFactory;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.CamelContextHelper;

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
        DataSource target = null;

        // endpoint options overrule component configured datasource
        DataSource ds = resolveAndRemoveReferenceParameter(parameters, "dataSource", DataSource.class);
        if (ds != null) {
            target = ds;
        }
        String dataSourceRef = getAndRemoveParameter(parameters, "dataSourceRef", String.class);
        if (target == null && dataSourceRef != null) {
            target = CamelContextHelper.mandatoryLookup(getCamelContext(), dataSourceRef, DataSource.class);
        }
        if (target == null) {
            // fallback and use component
            target = dataSource;
        }
        if (target == null) {
            throw new IllegalArgumentException("DataSource must be configured");
        }


        return new SqlStoredEndpoint(templateStoredProcedureFactory, target, remaining);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
