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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BrowseDevConsoleTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("seda:queue1");

                from("direct:other")
                        .to("seda:queue2");
            }
        };
    }

    @Test
    public void testBrowseConsoleText() throws Exception {
        // Send some messages to the seda queue
        template.sendBody("direct:start", "Message 1");
        template.sendBody("direct:start", "Message 2");

        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("browse");
        Assertions.assertNotNull(console);
        Assertions.assertEquals("camel", console.getGroup());
        Assertions.assertEquals("browse", console.getId());

        String out = (String) console.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
        log.info(out);
        Assertions.assertTrue(out.contains("seda://queue1"));
    }

    @Test
    public void testBrowseConsoleJson() throws Exception {
        // Send some messages to the seda queue
        template.sendBody("direct:start", "Message 1");
        template.sendBody("direct:start", "Message 2");

        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("browse");
        Assertions.assertNotNull(console);

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonArray browse = out.getCollection("browse");
        Assertions.assertNotNull(browse);
        Assertions.assertFalse(browse.isEmpty());

        // Find queue1
        boolean foundQueue1 = browse.stream()
                .map(o -> (JsonObject) o)
                .anyMatch(jo -> jo.getString("endpointUri").contains("queue1"));
        Assertions.assertTrue(foundQueue1);
    }

    @Test
    public void testBrowseConsoleWithFilter() throws Exception {
        // Send messages to both queues
        template.sendBody("direct:start", "Message for queue1");
        template.sendBody("direct:other", "Message for queue2");

        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("browse");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BrowseDevConsole.FILTER, ".*queue1.*");

        String out = (String) console.call(DevConsole.MediaType.TEXT, options);
        Assertions.assertNotNull(out);
        log.info(out);
        Assertions.assertTrue(out.contains("queue1"));
        Assertions.assertFalse(out.contains("queue2"));
    }

    @Test
    public void testBrowseConsoleWithLimit() throws Exception {
        // Send multiple messages
        for (int i = 1; i <= 5; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("browse");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BrowseDevConsole.LIMIT, "2");
        options.put(BrowseDevConsole.FILTER, ".*queue1.*");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonArray browse = out.getCollection("browse");
        Assertions.assertNotNull(browse);
        Assertions.assertFalse(browse.isEmpty());

        JsonObject endpoint = (JsonObject) browse.get(0);
        Assertions.assertEquals(2, endpoint.getInteger("limit").intValue());
    }

    @Test
    public void testBrowseConsoleStatusOnly() throws Exception {
        // Send some messages
        template.sendBody("direct:start", "Message 1");
        template.sendBody("direct:start", "Message 2");

        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("browse");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BrowseDevConsole.DUMP, "false");
        options.put(BrowseDevConsole.FILTER, ".*queue1.*");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonArray browse = out.getCollection("browse");
        Assertions.assertNotNull(browse);
        Assertions.assertFalse(browse.isEmpty());

        JsonObject endpoint = (JsonObject) browse.get(0);
        // Status only should have queueSize but no messages array
        Assertions.assertTrue(endpoint.containsKey("queueSize"));
        Assertions.assertFalse(endpoint.containsKey("messages"));
    }

    @Test
    public void testBrowseConsoleWithMessages() throws Exception {
        // Send some messages
        template.sendBody("direct:start", "Test Body Content");

        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("browse");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BrowseDevConsole.DUMP, "true");
        options.put(BrowseDevConsole.INCLUDE_BODY, "true");
        options.put(BrowseDevConsole.FILTER, ".*queue1.*");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonArray browse = out.getCollection("browse");
        Assertions.assertNotNull(browse);
        Assertions.assertFalse(browse.isEmpty());

        JsonObject endpoint = (JsonObject) browse.get(0);
        Assertions.assertTrue(endpoint.containsKey("messages"));

        JsonArray messages = endpoint.getCollection("messages");
        Assertions.assertNotNull(messages);
        Assertions.assertFalse(messages.isEmpty());
    }

    @Test
    public void testBrowseConsoleWithTail() throws Exception {
        // Send multiple messages
        for (int i = 1; i <= 5; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("browse");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BrowseDevConsole.TAIL, "2");
        options.put(BrowseDevConsole.FILTER, ".*queue1.*");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonArray browse = out.getCollection("browse");
        Assertions.assertNotNull(browse);
        Assertions.assertFalse(browse.isEmpty());

        JsonObject endpoint = (JsonObject) browse.get(0);
        // Position should indicate we started from later in the queue
        int position = endpoint.getInteger("position");
        Assertions.assertTrue(position > 0);
    }

    @Test
    public void testBrowseConsoleEmptyQueue() throws Exception {
        // Don't send any messages, queue should be empty

        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("browse");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BrowseDevConsole.FILTER, ".*queue1.*");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonArray browse = out.getCollection("browse");
        Assertions.assertNotNull(browse);
        Assertions.assertFalse(browse.isEmpty());

        JsonObject endpoint = (JsonObject) browse.get(0);
        Assertions.assertEquals(0, endpoint.getInteger("queueSize").intValue());
    }

}
