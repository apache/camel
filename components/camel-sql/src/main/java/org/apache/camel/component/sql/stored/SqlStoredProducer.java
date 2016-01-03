package org.apache.camel.component.sql.stored;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedure;
import org.apache.camel.impl.DefaultProducer;

/**
 * Created by snurmine on 1/3/16.
 */
public class SqlStoredProducer extends DefaultProducer {

    final TemplateStoredProcedure templateStoredProcedure;

    public SqlStoredProducer(Endpoint endpoint, TemplateStoredProcedure templateStoredProcedure) {
        super(endpoint);
        this.templateStoredProcedure = templateStoredProcedure;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        templateStoredProcedure.execute(exchange);
    }

}
