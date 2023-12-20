/*
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
package org.apache.camel.issues;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DynamicallyConcurrentlyAddRoutesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testConcurrentlyAddRoutes() throws Exception {

        context.start();

        Supplier<Callable<Boolean>> addRouteSupplier = () -> {
            Callable<Boolean> addRouteTask = () -> {
                String routeId = UUID.randomUUID().toString();
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        String endpointUri = "tmp:start_" + routeId;
                        MySlowEndpoint mySlowEndpoint = new MySlowEndpoint(endpointUri, context);
                        from(mySlowEndpoint).id(routeId).to("mock:result");
                        log.info("Route {} configured", endpointUri);
                    }
                });
                context.getRouteController().stopRoute(routeId, 30, TimeUnit.SECONDS, true);
                boolean result = context.removeRoute(routeId);
                log.info("Tried to remove route {}. Success? {}", routeId, result);
                return result;
            };
            return addRouteTask;
        };

        List<Callable<Boolean>> tasks = Stream.generate(addRouteSupplier).limit(4).toList();

        ExecutorService ex = Executors.newFixedThreadPool(4);

        try {
            List<Future<Boolean>> result = ex.invokeAll(tasks, 1, TimeUnit.MINUTES);

            ex.awaitTermination(10, TimeUnit.SECONDS);

            long failed = result.stream().filter(p -> {
                try {
                    return Boolean.FALSE.equals(p.get());
                } catch (Exception e) {
                    return false;
                }
            }).count();

            long successful = result.stream().filter(p -> {
                try {
                    return Boolean.TRUE.equals(p.get());
                } catch (Exception e) {
                    return false;
                }
            }).count();

            log.info("Success/Failed: {}/{}", successful, failed);
            assertEquals(4L, successful);
            assertEquals(0L, failed);

        } finally {
            ex.shutdown();
            context.stop();
        }

    }

    private static class MySlowEndpoint extends DefaultEndpoint {

        private static final Logger LOG = LoggerFactory.getLogger(MySlowEndpoint.class);

        public MySlowEndpoint(String endpointUri, CamelContext context) {
            super(endpointUri, null);
            setCamelContext(context);
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            LOG.info("Creating slow consumer");
            return new DefaultConsumer(this, processor) {
                @Override
                protected void doStart() throws Exception {
                    LOG.trace("Slow start.");
                    Thread.sleep(100);
                    super.doStart();
                    LOG.trace("Slow start done.");
                }
            };
        }

        @Override
        public Producer createProducer() throws Exception {
            return null;
        }
    }

}
