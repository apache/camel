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
package org.apache.camel.component.webhook;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.webhook.support.TestComponent;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class WebhookPathTest extends WebhookTestBase {

    @Test
    public void testComponentPath() {
        String result = template.requestBody("netty-http:http://localhost:" + port + "/comp", "", String.class);
        assertEquals("msg: webhook", result);
    }

    @Test
    public void testUriPath() {
        String result = template.requestBody("netty-http:http://localhost:" + port + "/uri", "", String.class);
        assertEquals("uri: webhook", result);
    }

    @Test(expected = CamelExecutionException.class)
    public void testRootPathError() {
        template.requestBody("netty-http:http://localhost:" + port, "", String.class);
    }

    @Override
    public CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        WebhookComponent comp = (WebhookComponent) context.getComponent("webhook");
        comp.getConfiguration().setWebhookPath("/comp");
        return context;
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("wb-delegate-component", new TestComponent(endpoint -> {
            endpoint.setWebhookHandler(proc -> ex -> {
                ex.getMessage().setBody("webhook");
                proc.process(ex);
            });
        }));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                restConfiguration()
                        .host("0.0.0.0")
                        .port(port);

                from("webhook:wb-delegate://xx")
                        .transform(body().prepend("msg: "));

                from("webhook:wb-delegate://xx?webhookPath=/uri")
                        .transform(body().prepend("uri: "));

            }
        };
    }
}
