package org.apache.camel.component.sql.stored;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedure;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedureFactory;
import org.apache.camel.impl.DefaultProducer;

/**
 * Created by snurmine on 1/3/16.
 */
public class SqlStoredProducer extends DefaultProducer {

    final TemplateStoredProcedureFactory templateStoredProcedureFactory;

    final TemplateStoredProcedure defaultTemplateStoredProcedure;

    public SqlStoredProducer(Endpoint endpoint, String template, TemplateStoredProcedureFactory
            templateStoredProcedureFactory) {
        super(endpoint);
        this.defaultTemplateStoredProcedure = templateStoredProcedureFactory.createFromString(template);
        this.templateStoredProcedureFactory = templateStoredProcedureFactory;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        this.defaultTemplateStoredProcedure.execute(exchange);
    }

}
