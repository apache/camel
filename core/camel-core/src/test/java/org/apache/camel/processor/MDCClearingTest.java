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
package org.apache.camel.processor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MDCClearingTest extends ContextTestSupport {

    public static final String CAMEL_BREADCRUMB_ID = "camel.breadcrumbId";
    public static final String BREADCRUMB_ID = "breadcrumbId";
    public static final String MY_BREADCRUMB = "my breadcrumb";

    private static final Logger LOG = LoggerFactory.getLogger(MDCClearingTest.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);

    @Test
    public void shouldPropagateAndClearMdcInSyncRoute() {
        // given
        MDC.remove(CAMEL_BREADCRUMB_ID);

        // when
        template.requestBodyAndHeader("direct:test-sync", "TEST", BREADCRUMB_ID, MY_BREADCRUMB);

        // then
        assertNull(MDC.get(CAMEL_BREADCRUMB_ID));
    }

    @Test
    public void shouldPropagateAndClearMdcInAsyncRoute() {
        // given
        MDC.remove(CAMEL_BREADCRUMB_ID);

        // when
        template.requestBodyAndHeader("direct:test-async", "TEST", BREADCRUMB_ID, MY_BREADCRUMB);

        // then
        assertNull(MDC.get(CAMEL_BREADCRUMB_ID));
    }

    @Test
    public void shouldPropagateAndClearMdcInMixedRoute() {
        // given
        MDC.remove(CAMEL_BREADCRUMB_ID);

        // when
        template.requestBodyAndHeader("direct:test-mixed", "TEST", BREADCRUMB_ID, MY_BREADCRUMB);

        // then
        assertNull(MDC.get(CAMEL_BREADCRUMB_ID));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setUseMDCLogging(true);
        camelContext.setUseBreadcrumb(true);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test-sync")
                        .process(new MySyncProcessor("STEP 1"));

                from("direct:test-async")
                        .process(new MyAsyncProcessor("STEP 2"));

                from("direct:test-mixed")
                        .process(new MyAsyncProcessor("STEP 3"))
                        .process(new MySyncProcessor("STEP 4"));
            }
        };
    }

    private static class MySyncProcessor implements Processor {
        private final String msg;

        public MySyncProcessor(String msg) {
            this.msg = msg;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            LOG.info(msg);
            assertEquals(MY_BREADCRUMB, MDC.get(CAMEL_BREADCRUMB_ID));
        }
    }

    private class MyAsyncProcessor extends AsyncProcessorSupport {

        private final String msg;

        public MyAsyncProcessor(String msg) {
            this.msg = msg;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            LOG.info(msg);
            assertEquals(MY_BREADCRUMB, MDC.get(CAMEL_BREADCRUMB_ID));

            executorService.execute(() -> {
                // wait a little to simulate later async completion
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // ignore
                }
                callback.done(false);
            });
            return false;
        }
    }
}
