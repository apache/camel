package org.apache.camel.component.jooq;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.jooq.UpdatableRecord;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/*
 * XML configuration test
 */
@ContextConfiguration(locations = {"/jooq-spring.xml", "/camel-context.xml"})
public class JooqXMLProducerTest extends BaseJooqTest {

    @Autowired
    CamelContext context;

    @Test
    public void testInsert() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        UpdatableRecord entity = (UpdatableRecord)producerTemplate.sendBody(context.getEndpoint("direct:insert"), ExchangePattern.InOut, "empty");
        Assert.assertNotNull(entity);
    }

    @Test
    public void testExecute() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody(context.getEndpoint("direct:execute"), ExchangePattern.InOut, "empty");
    }

    @Test
    public void testFetch() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody(context.getEndpoint("direct:fetch"), ExchangePattern.InOut, "empty");
    }
}
