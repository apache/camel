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

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The route input is named by the id of its from (from1), as the trace and the message history name it, not by the id
 * of the route (CAMEL-26864).
 */
class ProcessorDetailFromIdTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        // the processor-detail console reads the routes from JMX
        return true;
    }

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
    void theFromIsNamedByItsNodeId() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("processor-detail");
        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON, Map.of("routeId", "myRoute"));

        JsonObject from = (JsonObject) ((JsonArray) out.get("processors")).get(0);
        String fromId = ((ModelCamelContext) context).getRouteDefinition("myRoute").getInput().getId();
        Assertions.assertEquals("from", from.getString("type"));
        Assertions.assertNotNull(fromId);
        Assertions.assertEquals(fromId, from.getString("id"));
        Assertions.assertNotEquals("myRoute", from.getString("id"));
        Assertions.assertEquals("myRoute", out.getString("routeId"));
    }
}
