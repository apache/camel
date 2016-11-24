package org.apache.camel.component.azure.servicebus;

import org.apache.camel.builder.RouteBuilder;

/**
 * Created by alan on 24/10/16.
 */
public class SbTopicComponentTest extends SbComponentTest {
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            final String sbUrl = String.format("azure-sb://topic?topicPath=mytopic&subscriptionName=mysubcription&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true");
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to(sbUrl);

                from(sbUrl)
                        .to("mock:result");
            }
        };
    }
}
