package org.apache.camel.component.sip;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import javax.sip.message.Request;

public class MessageTest extends CamelTestSupport
{
    @EndpointInject(uri = "mock:notification")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;

    @Test
    public void testSendingMessage() throws Exception {
        resultEndpoint.expectedMinimumMessageCount(1);

        producerTemplate.sendBodyAndHeader(
                "sip://listener@localhost:5154?stackName=sender&fromUser=sender&fromHost=localhost&fromPort=5252" +
                        "&eventHeaderName=sendingToListener&eventId=message",
                "Hello from the other side",
                "REQUEST_METHOD", Request.MESSAGE);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                //Consumer which listens for incoming messages
                from("sip://listener@localhost:5154?stackName=Listener&eventHeaderName=retrievedFromSIP&eventId=SIP")
                        .to("log:ReceivedMessage")
                        .to("mock:notification");
            }
        };
    }

}