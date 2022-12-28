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

@DevConsole("type-converters")
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
        if (reg.getStatistics().isStatisticsEnabled()) {
            sb.append(String.format("\n    Attempts: %s", reg.getStatistics().getAttemptCounter()));
            sb.append(String.format("\n    Hit: %s", reg.getStatistics().getHitCounter()));
            sb.append(String.format("\n    Miss: %s", reg.getStatistics().getMissCounter()));
            sb.append(String.format("\n    Failed: %s", reg.getStatistics().getFailedCounter()));
            sb.append(String.format("\n    Noop: %s", reg.getStatistics().getNoopCounter()));
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        TypeConverterRegistry reg = getCamelContext().getTypeConverterRegistry();
        root.put("size", reg.size());
        root.put("exists", reg.getTypeConverterExists().name());
        root.put("existsLoggingLevel", reg.getTypeConverterExistsLoggingLevel().name());
        if (reg.getStatistics().isStatisticsEnabled()) {
            JsonObject props = new JsonObject();
            root.put("statistics", props);
            props.put("attemptCounter", reg.getStatistics().getAttemptCounter());
            props.put("hitCounter", reg.getStatistics().getHitCounter());
            props.put("missCounter", reg.getStatistics().getAttemptCounter());
            props.put("failedCounter", reg.getStatistics().getFailedCounter());
            props.put("noopCounter", reg.getStatistics().getNoopCounter());
        }

        return root;
    }
}
