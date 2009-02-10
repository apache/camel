package org.apache.camel.itest.http;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

@ContextConfiguration
public class HttpMaxConnectionPerHostTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject(uri = "direct:start")
    protected ProducerTemplate producer;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mock;

    public void testMocksIsValid() throws Exception {
        mock.expectedMessageCount(1);

        producer.sendBody(null);

        mock.assertIsSatisfied();
    }

}
