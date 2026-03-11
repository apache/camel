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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class DiagramScripts {

    private static final String BASE_PATH = "/org/apache/camel/dsl/jbang/core/commands/diagram/";
    private final Map<String, String> cache = new HashMap<>();

    String load(String name) {
        return cache.computeIfAbsent(name, DiagramScripts::readScript);
    }

    private static String readScript(String name) {
        String resource = BASE_PATH + name;
        try (InputStream inputStream = DiagramScripts.class.getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IllegalStateException("Unable to load diagram script: " + resource);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load diagram script: " + resource, e);
        }
    }
}
