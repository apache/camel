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
package org.apache.camel.component.jetty.rest;

import java.io.InputStream;
import java.net.URL;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class RestJettyRemoveAddRestAndRouteTest extends BaseJettyTest {

    @Test
    public void testCallRoute() throws Exception {
        InputStream stream = new URL("http://localhost:" + getPort() + "/issues/35").openStream();
        assertEquals("Here's your issue 35", IOUtils.toString(stream));

        stream = new URL("http://localhost:" + getPort() + "/listings").openStream();
        assertEquals("some listings", IOUtils.toString(stream));
    }

    @Test
    public void testRemoveAddRoute() throws Exception {
        context.getRouteController().stopRoute("issues");
        boolean removed = context.removeRoute("issues");

        assertTrue("Should have removed route", removed);

        try (InputStream stream = new URL("http://localhost:" + getPort() + "/issues/35").openStream()) {
            fail();
        } catch (Exception e) {
        }

        new RouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                rest("/").get("/issues/{isin}/{sedol}").route().id("issues")
                    .process(e -> e.getOut().setBody("Here's your issue " + e.getIn().getHeader("isin") + ":" + e.getIn().getHeader("sedol"))).endRest();
            }
        }.addRoutesToCamelContext(context);
        // exception here since we have 2 rest configurations
        // org.apache.camel.model.rest.RestDefinition.asRouteDefinition(CamelContext
        // camelContext) line 607 will have 2 rest contexts
        // and duplicate route definitions for the same route which will cause
        // exception

        InputStream stream = new URL("http://localhost:" + getPort() + "/issues/35/65").openStream();
        assertEquals("Here's your issue 35:65", IOUtils.toString(stream));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost").port(getPort());

                rest("/").get("/issues/{isin}").route().id("issues").process(e -> e.getOut().setBody("Here's your issue " + e.getIn().getHeader("isin"))).endRest().get("/listings")
                    .route().id("listings").process(e -> e.getOut().setBody("some listings"));
            }
        };
    }
}
