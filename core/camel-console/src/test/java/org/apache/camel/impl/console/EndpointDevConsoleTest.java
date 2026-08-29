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
package org.apache.camel.impl.console;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EndpointDevConsoleTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute").to("mock:result");
            }
        };
    }

    @Test
    public void testEndpointConsoleText() throws Exception {
        template.sendBody("direct:start", "Hello");

        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("endpoint");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("endpoint", con.getId());

        String out = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
        assertThat(out).contains("direct://start", "mock://result");
    }

    @Test
    public void testEndpointConsoleJson() throws Exception {
        template.sendBody("direct:start", "Hello");

        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("endpoint");
        Assertions.assertNotNull(con);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);

        Assertions.assertNotNull(out.getInteger("size"));
        Assertions.assertNotNull(out.getInteger("staticSize"));
        Assertions.assertNotNull(out.getInteger("dynamicSize"));
        Assertions.assertNotNull(out.getInteger("maximumCacheSize"));

        JsonArray endpoints = out.getCollection("endpoints");
        Assertions.assertNotNull(endpoints);
        Assertions.assertFalse(endpoints.isEmpty());

        boolean foundStart = endpoints.stream()
                .map(o -> (JsonObject) o)
                .anyMatch(jo -> "direct://start".equals(jo.getString("uri")));
        Assertions.assertTrue(foundStart);

        JsonObject entry = (JsonObject) endpoints.get(0);
        Assertions.assertNotNull(entry.getBoolean("remote"));
        Assertions.assertNotNull(entry.getBoolean("stub"));
    }
}
