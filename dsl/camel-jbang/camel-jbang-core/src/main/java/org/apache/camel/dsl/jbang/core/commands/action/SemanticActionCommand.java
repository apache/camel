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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.exceptionhandler.UsageErrorHandler;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

public abstract class SemanticActionCommand extends ActionBaseCommand implements UsageErrorHandler {

    @CommandLine.Parameters(description = "Name or pid of a running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = "--json", description = "Output a single JSON document")
    boolean json;

    @CommandLine.Option(names = "--timeout", defaultValue = "60000",
                        description = "Timeout in milliseconds waiting for the integration")
    long timeout = 60000;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec commandSpec;

    protected SemanticActionCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() {
        try {
            if (timeout <= 0) {
                return error(2, "--timeout must be greater than zero", null);
            }
            JsonObject request;
            try {
                request = request();
            } catch (IllegalArgumentException e) {
                return error(2, e.getMessage(), null);
            }
            List<Long> pids = findPids(name).stream().filter(this::isRunningIntegration).toList();
            if (pids.isEmpty()) {
                return error(3, "No running Camel integration matches: " + name, null);
            }
            if (pids.size() != 1) {
                return error(3, "Multiple Camel integrations match " + name + ": " + pids + ". Specify a pid.", null);
            }
            long pid = pids.get(0);
            JsonObject status = loadStatus(pid);
            JsonObject context = status == null ? null : status.getMap("context");
            String version = context == null ? null : context.getString("version");
            if (version != null && version.matches("\\d+\\.\\d+(?:\\..*)?") && !VersionHelper.isGE(version, "4.23")) {
                return error(3, "Semantic tooling requires Camel 4.23 or newer; integration " + pid
                                + " runs Camel " + version,
                        null);
            }
            JsonObject response = RuntimeHelper.executeAction(pid, request, timeout);
            if (response == null) {
                return error(4, "No reply from integration within " + timeout + " milliseconds", null);
            }
            if (response.isEmpty()) {
                return error(3, "Semantic tooling is unavailable; the integration needs a matching camel-semantic version",
                        null);
            }
            if ("failed".equals(response.getString("status"))) {
                String message = response.getString("error");
                if (message == null || message.isBlank()) {
                    message = "Semantic action failed";
                }
                return error(1, message, response);
            }
            return printResponse(response);
        } catch (Exception e) {
            e.printStackTrace(commandSpec.commandLine().getErr());
            return error(70, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), null);
        }
    }

    private boolean isRunningIntegration(long pid) {
        JsonObject status = loadStatus(pid);
        JsonObject context = status == null ? null : status.getMap("context");
        return context != null && context.getIntegerOrDefault("phase", 0) < 9
                && ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent();
    }

    @Override
    public Integer usageError(String message, String[] args) {
        json = Arrays.stream(args).takeWhile(arg -> !"--".equals(arg))
                .anyMatch(arg -> "--json".equals(arg) || "--json=true".equals(arg));
        return json ? error(2, message, null) : null;
    }

    protected int error(int code, String message, JsonObject result) {
        commandSpec.commandLine().getErr().println(message);
        if (json) {
            JsonObject response = new JsonObject();
            if (result != null) {
                response.putAll(result);
            }
            response.put("status", "error");
            response.put("code", code);
            response.put("message", message);
            printer().println(response.toJson());
        } else if (result != null) {
            render(result);
        }
        return code;
    }

    protected int printResponse(JsonObject response) {
        if (json) {
            printer().println(response.toJson());
        } else {
            render(response);
        }
        return 0;
    }

    protected abstract JsonObject request();

    protected abstract void render(JsonObject response);
}
