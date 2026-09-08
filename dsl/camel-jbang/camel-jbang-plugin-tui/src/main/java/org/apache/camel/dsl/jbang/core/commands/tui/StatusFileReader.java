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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Reads the full status document that camel-cli-connector writes for each running integration to
 * {@code ~/.camel/<pid>-status.json}. The tabs show a digest of it; the AI tools and MCP resources hand out whole
 * top-level sections so a model can fetch exactly the piece it needs.
 */
final class StatusFileReader {

    static final String SECTION_LIST = "sections";

    private final Path camelDir;

    StatusFileReader(Path camelDir) {
        this.camelDir = camelDir;
    }

    static StatusFileReader defaultReader() {
        return new StatusFileReader(CommandLineHelper.getCamelDir());
    }

    Path statusFile(String pid) {
        return camelDir.resolve(pid + "-status.json");
    }

    /**
     * The parsed document, or {@code null} when there is no readable status file for the pid.
     */
    JsonObject read(String pid) {
        if (pid == null || pid.isBlank()) {
            return null;
        }
        Path file = statusFile(pid.trim());
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return (JsonObject) Jsoner.deserialize(text);
        } catch (Exception e) {
            // the connector rewrites the file every second, a torn read is expected now and then
            return null;
        }
    }

    /**
     * Top-level section names of the document, in file order, or an empty list when it cannot be read.
     */
    List<String> sections(String pid) {
        JsonObject doc = read(pid);
        return doc == null ? List.of() : List.copyOf(doc.keySet());
    }

    /**
     * One top-level section, or {@code null} when the document or the section does not exist.
     */
    Object section(String pid, String section) {
        JsonObject doc = read(pid);
        return doc == null || section == null ? null : doc.get(section);
    }
}
