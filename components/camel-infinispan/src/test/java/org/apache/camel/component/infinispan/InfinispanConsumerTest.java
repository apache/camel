package org.apache.camel.component.infinispan;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class InfinispanConsumerTest extends InfinispanTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockResult;

    @Test
    public void consumerReceivedPreAndPostEntryCreatedEventNotifications() throws Exception {
        mockResult.expectedMessageCount(2);

        mockResult.message(0).outHeader(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_CREATED");
        mockResult.message(0).outHeader(InfinispanConstants.IS_PRE).isEqualTo(true);
        mockResult.message(0).outHeader(InfinispanConstants.CACHE_NAME).isNotNull();
        mockResult.message(0).outHeader(InfinispanConstants.KEY).isEqualTo(KEY_ONE);

        mockResult.message(1).outHeader(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_CREATED");
        mockResult.message(1).outHeader(InfinispanConstants.IS_PRE).isEqualTo(false);
        mockResult.message(1).outHeader(InfinispanConstants.CACHE_NAME).isNotNull();
        mockResult.message(1).outHeader(InfinispanConstants.KEY).isEqualTo(KEY_ONE);

        currentCache().put(KEY_ONE, VALUE_ONE);
        mockResult.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("infinispan://localhost?cacheContainer=#cacheContainer&sync=false&eventTypes=CACHE_ENTRY_CREATED")
                        .to("mock:result");
            }
        };
    }
}

