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
package org.apache.camel.component.quartz;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class QuartzConsoleTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("quartz://myGroup/myTimerName?cron=0/2+*+*+*+*+?").routeId("myRoute")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testQuartzConsoleText() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("quartz");
        assertNotNull(con);
        assertEquals("camel", con.getGroup());
        assertEquals("quartz", con.getId());

        String text = (String) con.call(DevConsole.MediaType.TEXT);
        assertNotNull(text);
    }

    @Test
    public void testQuartzConsoleJson() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("quartz");
        assertNotNull(con);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        assertNotNull(out);
        assertNotNull(out.getString("schedulerName"));

        JsonArray triggers = out.getCollection("triggers");
        assertNotNull(triggers);
        assertFalse(triggers.isEmpty());

        JsonObject trigger = (JsonObject) triggers.get(0);
        assertEquals("cron", trigger.getString("triggerType"));
    }
}
