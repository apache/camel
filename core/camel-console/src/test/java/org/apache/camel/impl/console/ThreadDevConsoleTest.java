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
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ThreadDevConsoleTest extends ContextTestSupport {

    @Test
    public void testThreadConsoleText() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("thread");
        Assertions.assertNotNull(console);
        Assertions.assertEquals("jvm", console.getGroup());
        Assertions.assertEquals("thread", console.getId());

        String out = (String) console.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
        log.info(out);
        Assertions.assertTrue(out.contains("Threads:"));
        Assertions.assertTrue(out.contains("Daemon Threads:"));
        Assertions.assertTrue(out.contains("Total Started Threads:"));
        Assertions.assertTrue(out.contains("Peak Threads:"));
    }

    @Test
    public void testThreadConsoleJson() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("thread");
        Assertions.assertNotNull(console);

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        Assertions.assertNotNull(out.get("threadCount"));
        Assertions.assertNotNull(out.get("daemonThreadCount"));
        Assertions.assertNotNull(out.get("totalStartedThreadCount"));
        Assertions.assertNotNull(out.get("peakThreadCount"));

        JsonArray threads = out.getCollection("threads");
        Assertions.assertNotNull(threads);
        Assertions.assertFalse(threads.isEmpty());

        // Check first thread has required fields
        JsonObject thread = (JsonObject) threads.get(0);
        Assertions.assertNotNull(thread.get("id"));
        Assertions.assertNotNull(thread.getString("name"));
        Assertions.assertNotNull(thread.getString("state"));
    }

    @Test
    public void testThreadConsoleWithStackTrace() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("thread");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put("stackTrace", "true");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);

        JsonArray threads = out.getCollection("threads");
        Assertions.assertNotNull(threads);
        Assertions.assertFalse(threads.isEmpty());

        // At least one thread should have a stack trace
        boolean hasStackTrace = threads.stream()
                .map(t -> (JsonObject) t)
                .anyMatch(t -> t.containsKey("stackTrace"));
        Assertions.assertTrue(hasStackTrace);
    }
}
