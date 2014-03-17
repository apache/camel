package org.apache.camel.component.sjms.consumer;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Concurrent consumer with JMSReply test.
 */
public class InOutConcurrentConsumerTest extends JmsTestSupport {

    @EndpointInject(uri = "mock:result")
    MockEndpoint result;

    @Test
    public void testConcurrent() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int messages, int poolSize) throws Exception {

        result.expectedMessageCount(messages);
        result.expectsNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        final List<Future<String>> futures = new ArrayList<Future<String>>();
        for (int i = 0; i < messages; i++) {
            final int index = i;
            Future<String> out = executor.submit(new Callable<String>() {
                public String call() throws Exception {
                    return template.requestBody("direct:start", "Message " + index, String.class);
                }
            });
            futures.add(out);
        }

        assertMockEndpointsSatisfied();

        for (int i = 0; i < futures.size(); i++) {
            Object out = futures.get(i).get();
            assertEquals("Bye Message " + i, out);
        }
        executor.shutdownNow();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .to("sjms:a?consumerCount=5&exchangePattern=InOut&namedReplyTo=myResponse")
                    .to("mock:result");

                from("sjms:a?consumerCount=5&exchangePattern=InOut&namedReplyTo=myResponse")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String body = exchange.getIn().getBody(String.class);
                            // sleep a little to simulate heavy work and force concurrency processing
                            Thread.sleep(1000);
                            exchange.getOut().setBody("Bye " + body);
                            exchange.getOut().setHeader("threadName", Thread.currentThread().getName());
                            System.out.println("Thread ID : " + Thread.currentThread().getName());
                        }
                    });
            }
        };
    }

}

