package org.apache.camel.component.azure.servicebus.integration;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.azure.servicebus.SbConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URLEncoder;

@Ignore("Integration test! Must be run manually.")
public class SbComponentsIntegrationTest extends CamelTestSupport {
    private String namespace = "alanliu";
    private String serviceBusRootUri = ".servicebus.windows.net";
    private String sasKeyName = "RootManageSharedAccessKey";
    private String sasKey = "lbtrbyCl5CfLQURx9FqdoHxHy+tNRdk1lLIjk8Hh+Ms=";
    private String connectionString = "Endpoint=sb://alanliu.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=lbtrbyCl5CfLQURx9FqdoHxHy+tNRdk1lLIjk8Hh+Ms=\n";
    private String queueName = "myqueue";
    private String topicName = "mytopic1";
    private String profile = "yyy";

    @EndpointInject(uri = "direct:start")
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertMockEndpointsSatisfied();

        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals("This is my message text.", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.BROKER_PROPERTIES));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.CONTENT_TYPE));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.DATE));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.CUSTOM_PROPERTIES));

        assertNotNull(exchange.getIn().getHeader(SbConstants.BROKER_PROPERTIES));
        assertEquals("This is my message text.", exchange.getIn().getBody());
    }
/*
Sample azure servicebus message:

HTTP/1.1 201 Created
Transfer-Encoding: chunked
Content-Type: application/atom+xml;type=entry;charset=utf-8
Location: https://your-namespace.servicebus.windows.net/httpclientsamplequeue/messages/2/7da9cfd5-40d5-4bb1-8d64-ec5a52e1c547
Server: Microsoft-HTTPAPI/2.0
BrokerProperties: {"DeliveryCount":1,"EnqueuedSequenceNumber":0,"EnqueuedTimeUtc":"Wed, 02 Jul 2014 01:32:27 GMT","Label":"M1","LockToken":"7da9cfd5-40d5-4bb1-8d64-ec5a52e1c547","LockedUntilUtc":"Wed, 02 Jul 2014 01:33:27 GMT","MessageId":"31907572164743c38741631acd554d6f","SequenceNumber":2,"State":"Active","TimeToLive":10}
Priority: "High"
Customer: "12345,ABC"
Date: Wed, 02 Jul 2014 01:32:27 GMT

12
This is a message.
0
* */
    @Test
    public void sendInOut() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertMockEndpointsSatisfied();
        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals("This is my message text.", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.BROKER_PROPERTIES));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.CONTENT_TYPE));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.DATE));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.CUSTOM_PROPERTIES));

        assertNotNull(exchange.getOut().getHeader(SbConstants.BROKER_PROPERTIES));
        assertEquals("This is my message text.", exchange.getOut().getBody());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        final String queueEndpointUri = String.format("azure-sb://%s%s/%s?sasKeyName=%s&sasKey=%s",
                namespace, serviceBusRootUri, queueName, sasKeyName, URLEncoder.encode(sasKey, "UTF-8"));
//        final String topicEndpointUri = String.format("azure-sb://%s%s/%s?sasKeyName=%s&sasKey=%s",
//                namespace, serviceBusRootUri, topicName, sasKeyName, URLEncoder.encode(sasKey, "UTF-8"));
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to(queueEndpointUri);

                from(queueEndpointUri)
                        .to("mock:result");
            }
        };
    }
}
