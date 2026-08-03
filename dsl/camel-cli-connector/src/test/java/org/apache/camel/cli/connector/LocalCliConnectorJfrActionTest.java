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
import java.nio.file.Files;
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

        JsonObject request = new JsonObject();
        request.put("action", "jfr");
        request.put("command", "disable");
        request.put("event", "route");
        File outputFile = writeActionAndStartConnector(request);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonObject json = readOutput(outputFile);
            assertThat(json).containsEntry("command", "disable").containsEntry("event", "route");
        });
    }

    @Test
    void reportsAnErrorWhenTheJfrConsoleIsNotOnTheClasspath() throws Exception {
        // no jfr console is registered here, so the caller must be told why rather than being handed an empty file
        JsonObject request = new JsonObject();
        request.put("action", "jfr");
        request.put("command", "status");
        File outputFile = writeActionAndStartConnector(request);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonObject json = readOutput(outputFile);
            assertThat(json.getString("error")).contains("camel-jfr is not on the classpath");
        });
    }

    /**
     * Writes the action file <b>before</b> the connector is started, and returns the output file to wait on.
     * <p>
     * The connector schedules its poll with an initial delay of zero, so a poll fires while {@code start()} is still
     * returning. Writing the request afterwards lets that first poll read a half-written file, and the connector
     * deletes every action file it has looked at, so the request would be silently dropped. Writing first is
     * deterministic: {@code createLockFile} only creates the file when it is missing, so the connector picks the
     * complete request up on its very first poll.
     */
    private File writeActionAndStartConnector(JsonObject request) throws Exception {
        long pid = ProcessHandle.current().pid();
        File dir = new File(System.getProperty("user.home"), ".camel");
        File actionFile = new File(dir, pid + "-action.json");
        File outputFile = new File(dir, pid + "-output.json");
        // the output file is keyed on the pid, so a leftover from another test would be read as this test's answer
        Files.deleteIfExists(outputFile.toPath());
        IOHelper.writeText(request.toJson(), actionFile);

        connector = new LocalCliConnector(new DefaultCliConnectorFactory());
        connector.setCamelContext(context);
        connector.start();
        return outputFile;
    }

    /**
     * The connector polls for the action file, so the output file does not exist yet on the first poll. Asserting on
     * its existence keeps the wait retrying instead of failing on a FileNotFoundException.
     */
    private static JsonObject readOutput(File outputFile) throws Exception {
        assertThat(outputFile).exists();
        String text = IOHelper.loadText(new FileInputStream(outputFile));
        assertThat(text).isNotBlank();
        return (JsonObject) Jsoner.deserialize(text);
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
