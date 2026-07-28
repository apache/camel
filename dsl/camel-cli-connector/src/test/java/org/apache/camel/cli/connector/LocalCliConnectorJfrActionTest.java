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
package org.apache.camel.cli.connector;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LocalCliConnectorJfrActionTest extends CamelTestSupport {

    private LocalCliConnector connector;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }

    @AfterEach
    void stopConnector() {
        if (connector != null) {
            connector.stop();
        }
    }

    @Test
    void dispatchesJfrActionToJfrDevConsole() throws Exception {
        RecordingDevConsole console = new RecordingDevConsole();
        DevConsoleRegistry.get(context).register(console);

        connector = new LocalCliConnector(new DefaultCliConnectorFactory());
        connector.setCamelContext(context);
        connector.start();

        long pid = ProcessHandle.current().pid();
        File dir = new File(System.getProperty("user.home"), ".camel");
        File actionFile = new File(dir, pid + "-action.json");
        File outputFile = new File(dir, pid + "-output.json");

        JsonObject request = new JsonObject();
        request.put("action", "jfr");
        request.put("command", "disable");
        request.put("event", "route");
        IOHelper.writeText(request.toJson(), actionFile);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String text = IOHelper.loadText(new FileInputStream(outputFile));
            assertThat(text).isNotBlank();
            JsonObject json = (JsonObject) Jsoner.deserialize(text);
            assertThat(json).containsEntry("command", "disable").containsEntry("event", "route");
        });
    }

    private static class RecordingDevConsole extends AbstractDevConsole {

        RecordingDevConsole() {
            super("test", "jfr", "Fake JFR Console", "fake jfr console for testing action dispatch");
        }

        @Override
        protected String doCallText(Map<String, Object> options) {
            return String.valueOf(options);
        }

        @Override
        protected Map<String, Object> doCallJson(Map<String, Object> options) {
            JsonObject json = new JsonObject();
            options.forEach(json::put);
            return json;
        }
    }
}
