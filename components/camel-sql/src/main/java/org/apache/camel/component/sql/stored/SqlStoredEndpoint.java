package org.apache.camel.component.sql.stored;

import org.apache.camel.Producer;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedureFactory;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

import javax.sql.DataSource;

@UriEndpoint(scheme = "sql-stored", title = "SQL stored", syntax = "sql-stored:template", label = "database,sql")
public class SqlStoredEndpoint extends DefaultPollingEndpoint {

    @UriPath(description = "Sets the stored procedure template to perform")
    @Metadata(required = "true")
    private String template;

    private final TemplateStoredProcedureFactory templateStoredProcedureFactory;

    private final DataSource dataSource;


    public SqlStoredEndpoint(TemplateStoredProcedureFactory templateStoredProcedureFactory, DataSource dataSource,
                             String template) {
        this.templateStoredProcedureFactory = templateStoredProcedureFactory;
        this.dataSource = dataSource;
        this.template = template;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SqlStoredProducer(this, templateStoredProcedureFactory.createFromString(template, dataSource));
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    protected String createEndpointUri() {
        return "sql-stored:" + UnsafeUriCharactersEncoder.encode(template);
    }
}
