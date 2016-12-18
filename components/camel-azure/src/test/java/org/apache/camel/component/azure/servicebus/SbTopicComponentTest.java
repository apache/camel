package org.apache.camel.component.azure.servicebus;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SbTopicComponentTest extends CamelTestSupport {
    @EndpointInject(uri = "mock:result")
    protected MockEndpoint result;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void sendMessage() throws Exception {
        String expectedBody = "This is another message text.";

        result.expectedMessageCount(1);
        result.expectedBodiesReceived(expectedBody);
//        result.expectedHeaderReceived(SbConstants.BROKER_PROPERTIES, "");
//        result.expectedHeaderReceived(SbConstants.CONTENT_TYPE, "");
//        result.expectedHeaderReceived(SbConstants.DATE, "");
//        result.expectedHeaderReceived(SbConstants.CUSTOM_PROPERTIES, "");

        template.sendBody(expectedBody) ;
        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("MyServiceBusContract", new ServiceBusContractMock());

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("azure-sb://topic?topicPath=mytopic&subscriptionName=mysubcription&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true")
                        .to("mock:result");
            }
        };
    }
}
