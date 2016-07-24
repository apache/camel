/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.async;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RoutingSlip;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;
import org.junit.Assert;

/**
 * @version 
 */
public class AsyncEndpointRoutingSlipBeanNonBlockingTest extends ContextTestSupport {
    private AsyncCallback innerCallback;
    private Exchange innerExchange;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBean", new MyRoutingSlipBean());
        return jndi;
    }

    public void testAsyncEndpointDontBlock() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");
        Endpoint startEndpoint = context.getEndpoint("direct:start");
        SendProcessor asyncSender = new SendProcessor(startEndpoint);
        ServiceHelper.startService(asyncSender);

        ExecutorService executorService = context.getExecutorServiceManager().newSingleThreadExecutor(this, "test");
        try {
            Future<Boolean> asyncFuture = executorService.submit(new ExchangeSubmitter(startEndpoint, asyncSender));
            Assert.assertFalse(asyncFuture.get(5, TimeUnit.SECONDS));
            innerExchange.getOut().setBody("Bye Camel");
            innerCallback.done(false);

            assertMockEndpointsSatisfied();
        } finally {
            executorService.shutdown();
            ServiceHelper.stopAndShutdownService(asyncSender);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                    .to("bean:myBean");
                from("direct:asyncRoute")
                    .process(new MyAsyncProcessor());
            }
        };
    }

    public static class MyRoutingSlipBean {

        @RoutingSlip
        public String doSomething() {
            return "direct:asyncRoute,mock:result";
        }
    }

    private static class ExchangeSubmitter implements Callable<Boolean> {
        private final Endpoint startEndpoint;
        private final SendProcessor asyncSender;

        ExchangeSubmitter(Endpoint startEndpoint, SendProcessor asyncSender) {
            this.startEndpoint = startEndpoint;
            this.asyncSender = asyncSender;
        }

        @Override
        public Boolean call() throws Exception {
            Exchange exchange = startEndpoint.createExchange(ExchangePattern.InOut);
            exchange.getIn().setBody("Hello Camel");
            return asyncSender.process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    Assert.assertFalse(doneSync);
                }
            });
        }
    }

    private class MyAsyncProcessor implements AsyncProcessor {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            innerCallback = callback;
            innerExchange = exchange;

            return false;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            AsyncProcessorHelper.process(this, exchange);
        }
    }
}