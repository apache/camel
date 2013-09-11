package org.apache.camel.component.infinispan;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.junit.Test;

public class InfinispanSyncConsumerTest extends InfinispanTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockResult;

    @Test
    public void consumerReceivedPreAndPostEntryCreatedEventNotifications() throws Exception {
        mockResult.expectedMessageCount(2);
        mockResult.setMinimumResultWaitTime(900);

        currentCache().put(KEY_ONE, VALUE_ONE);
        mockResult.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("infinispan://localhost?cacheContainer=#cacheContainer&sync=false&eventTypes=CACHE_ENTRY_CREATED")

                        .delayer(500)
                        .to("mock:result");
            }
        };
    }
}

