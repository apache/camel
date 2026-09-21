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
package org.apache.camel.component.servlet;

import java.util.concurrent.CompletableFuture;

import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * async=true must still write an error response when the route fails after resuming on another thread, or when the
 * processor's stage completes exceptionally.
 */
public class ServletAsyncErrorTest extends ServletCamelRouterTestSupport {

    @Test
    public void testAsyncRouteThrows() throws Exception {
        WebResponse response = query(new GetMethodWebRequest(contextUrl + "/services/async-error"), false);

        assertEquals(500, response.getResponseCode());
    }

    @Test
    public void testAsyncStageCompletesExceptionally() throws Exception {
        // a route processor never completes the stage exceptionally (the exception lands on the exchange), so
        // plug a consumer with a custom AsyncProcessor directly
        ServletEndpoint endpoint = context.getEndpoint("servlet:///async-failed", ServletEndpoint.class);
        ServletConsumer consumer = (ServletConsumer) endpoint.createConsumer(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                callback.done(true);
                return true;
            }

            @Override
            public CompletableFuture<Exchange> processAsync(Exchange exchange) {
                return CompletableFuture.failedFuture(new IllegalStateException("stage failed"));
            }
        });
        consumer.start();
        try {
            WebResponse response = query(new GetMethodWebRequest(contextUrl + "/services/async-failed"), false);

            assertEquals(500, response.getResponseCode());
        } finally {
            consumer.stop();
        }
    }

    @Override
    protected DeploymentInfo getDeploymentInfo() {
        return Servlets.deployment()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath(CONTEXT)
                .setDeploymentName(getClass().getName())
                .addServlet(Servlets.servlet("CamelServlet", CamelHttpTransportServlet.class)
                        .addInitParam("async", "true")
                        .setAsyncSupported(true)
                        .addMapping("/services/*"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:///async-error")
                        // resumes on another thread, then fails
                        .delay(100).asyncDelayed().end()
                        .process(e -> {
                            throw new IllegalStateException("boom");
                        });
            }
        };
    }

}
