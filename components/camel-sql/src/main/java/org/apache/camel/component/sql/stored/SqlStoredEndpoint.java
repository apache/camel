package org.apache.camel.component.sql.stored;

import org.apache.camel.Producer;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedureFactory;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

@UriEndpoint(scheme = "sql-stored", title = "SQL stored", syntax = "sql-stored:template", label = "database,sql")
public class SqlStoredEndpoint extends DefaultPollingEndpoint {

    @UriPath(description = "Sets the stored procedure template to perform")
    @Metadata(required = "true")
    private String template;

    private final TemplateStoredProcedureFactory templateStoredProcedureFactory;

    private final JdbcTemplate jdbcTemplate;


    public SqlStoredEndpoint(JdbcTemplate jdbcTemplate,
                             String template) {
        this.templateStoredProcedureFactory = new TemplateStoredProcedureFactory(jdbcTemplate);
        this.jdbcTemplate = jdbcTemplate;
        this.template = template;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SqlStoredProducer(this, template, templateStoredProcedureFactory);
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
