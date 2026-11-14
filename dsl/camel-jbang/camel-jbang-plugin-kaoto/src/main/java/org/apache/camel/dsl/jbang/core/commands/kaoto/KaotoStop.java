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
package org.apache.camel.dsl.jbang.core.commands.kaoto;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine.Command;

@Command(name = "stop", description = "Stop the running Kaoto editor")
public class KaotoStop extends CamelCommand {

    public KaotoStop(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Path stateFile = Path.of(KaotoStart.STATE_FILE).toAbsolutePath();

        if (!Files.exists(stateFile)) {
            printer().printErr("No running Kaoto instance found in the current directory");
            return 1;
        }

        String content = Files.readString(stateFile).trim();
        int port;
        try {
            port = Integer.parseInt(content);
        } catch (NumberFormatException e) {
            printer().printErr("Invalid state file");
            Files.deleteIfExists(stateFile);
            return 1;
        }

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/shutdown"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (ConnectException e) {
            // server already stopped
        }

        Files.deleteIfExists(stateFile);
        printer().println("Kaoto stopped");

        return 0;
    }
}
