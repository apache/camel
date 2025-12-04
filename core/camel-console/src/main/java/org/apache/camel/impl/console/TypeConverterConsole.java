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

import java.util.Map;

import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "type-converters", description = "Camel Type Converter information")
public class TypeConverterConsole extends AbstractDevConsole {

    public TypeConverterConsole() {
        super("camel", "type-converters", "Type Converters", "Camel Type Converter information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        TypeConverterRegistry reg = getCamelContext().getTypeConverterRegistry();
        sb.append(String.format("\n    Converters: %s", reg.size()));
        sb.append(String.format("\n    Exists: %s", reg.getTypeConverterExists().name()));
        sb.append(String.format("\n    Exists LoggingLevel: %s", reg.getTypeConverterExistsLoggingLevel()));
        final TypeConverterRegistry.Statistics statistics = reg.getStatistics();

        statistics.computeIfEnabled(
                statistics::getAttemptCounter, v -> sb.append(String.format("\n    Attempts: %s", v)));
        statistics.computeIfEnabled(statistics::getHitCounter, v -> sb.append(String.format("\n    Hit: %s", v)));
        statistics.computeIfEnabled(statistics::getMissCounter, v -> sb.append(String.format("\n    Miss: %s", v)));
        statistics.computeIfEnabled(statistics::getFailedCounter, v -> sb.append(String.format("\n    Failed: %s", v)));
        statistics.computeIfEnabled(statistics::getNoopCounter, v -> sb.append(String.format("\n    Noop: %s", v)));

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        TypeConverterRegistry reg = getCamelContext().getTypeConverterRegistry();
        root.put("size", reg.size());
        root.put("exists", reg.getTypeConverterExists().name());
        root.put("existsLoggingLevel", reg.getTypeConverterExistsLoggingLevel().name());

        final TypeConverterRegistry.Statistics statistics = reg.getStatistics();
        JsonObject props = new JsonObject();

        statistics.computeIfEnabled(statistics::getAttemptCounter, v -> props.put("attemptCounter", v));
        statistics.computeIfEnabled(statistics::getHitCounter, v -> props.put("hitCounter", v));
        statistics.computeIfEnabled(statistics::getMissCounter, v -> props.put("missCounter", v));
        statistics.computeIfEnabled(statistics::getFailedCounter, v -> props.put("failedCounter", v));
        statistics.computeIfEnabled(statistics::getFailedCounter, v -> props.put("noopCounter", v));

        if (!props.isEmpty()) {
            root.put("statistics", props);
        }

        return root;
    }
}
