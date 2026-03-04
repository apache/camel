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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.version.VersionList;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * MCP Tools for querying available Camel versions using Quarkus MCP Server.
 */
@ApplicationScoped
public class VersionTools {

    /**
     * Tool to list available Camel versions for a specific runtime.
     */
    @Tool(description = "List available Camel versions for a specific runtime (main, spring-boot, quarkus). " +
                        "Returns version information including release date, JDK requirements, and LTS status.")
    public VersionListResult camel_version_list(
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Only show LTS (Long Term Support) releases (default: false)") Boolean lts,
            @ToolArg(description = "Minimum Camel version to include (e.g., 4.0)") String fromVersion,
            @ToolArg(description = "Maximum number of versions to return (default: 10)") Integer limit) {

        try {
            StringPrinter printer = new StringPrinter();
            CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

            VersionList versionList = new VersionList(main);
            versionList.runtime = resolveRuntime(runtime);
            versionList.jsonOutput = true;
            versionList.lts = lts != null && lts;
            versionList.fromVersion = fromVersion != null ? fromVersion : "4.0";
            versionList.sort = "-version"; // newest first
            versionList.download = true;
            versionList.fresh = false;

            int exitCode = versionList.doCall();
            if (exitCode != 0) {
                throw new ToolCallException("Failed to list versions, exit code: " + exitCode, null);
            }

            String jsonOutput = printer.getOutput();
            JsonArray versions = (JsonArray) Jsoner.deserialize(jsonOutput);

            List<VersionInfo> versionInfos = new ArrayList<>();
            int maxResults = limit != null ? limit : 10;
            int count = 0;

            for (Object obj : versions) {
                if (count >= maxResults) {
                    break;
                }
                JsonObject v = (JsonObject) obj;
                versionInfos.add(new VersionInfo(
                        v.getString("camelVersion"),
                        v.getString("runtime"),
                        v.getString("runtimeVersion"),
                        v.getString("quarkusVersion"),
                        v.getString("jdkVersion"),
                        v.getString("kind"),
                        v.getString("releaseDate"),
                        v.getString("eolDate")));
                count++;
            }

            return new VersionListResult(versionInfos.size(), versionInfos);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to list versions (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    private RuntimeType resolveRuntime(String runtime) {
        if (runtime == null || runtime.isBlank() || "main".equalsIgnoreCase(runtime)) {
            return RuntimeType.main;
        }
        try {
            return RuntimeType.fromValue(runtime);
        } catch (IllegalArgumentException e) {
            throw new ToolCallException(
                    "Unsupported runtime: " + runtime + ". Supported values are: main, spring-boot, quarkus", null);
        }
    }

    // Result classes for Jackson serialization

    public record VersionListResult(int count, List<VersionInfo> versions) {
    }

    public record VersionInfo(
            String camelVersion,
            String runtime,
            String runtimeVersion,
            String quarkusVersion,
            String jdkVersion,
            String kind,
            String releaseDate,
            String eolDate) {
    }
}
