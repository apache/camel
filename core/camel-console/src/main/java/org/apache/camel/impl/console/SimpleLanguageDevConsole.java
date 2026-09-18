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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.SimpleFunctionRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "simple-language", displayName = "Simple Language", description = "Display simple language details")
public class SimpleLanguageDevConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "Number of core simple functions") int coreSize,
            @Metadata(description = "Number of custom simple functions") int customSize,
            @Metadata(description = "The core function names (only present when there are any)") List<String> coreFunctions,
            @Metadata(description = "The custom function names (only present when there are any)") List<String> customFunctions) {
    }

    public SimpleLanguageDevConsole() {
        super("camel", "simple-language", "Simple Language", "Display simple language details");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        SimpleFunctionRegistry reg = PluginHelper.getSimpleFunctionRegistry(getCamelContext());
        sb.append(String.format("%n    Core Functions: %d", reg.coreSize()));
        for (String name : reg.getCoreFunctionNames()) {
            sb.append(String.format("%n        %s", name));
        }
        sb.append("\n");
        sb.append(String.format("%n    Custom Functions: %d", reg.customSize()));
        for (String name : reg.getCustomFunctionNames()) {
            sb.append(String.format("%n        %s", name));
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        SimpleFunctionRegistry reg = PluginHelper.getSimpleFunctionRegistry(getCamelContext());

        List<String> core = new ArrayList<>(reg.getCoreFunctionNames());
        List<String> custom = new ArrayList<>(reg.getCustomFunctionNames());

        Response response = new Response(
                reg.coreSize(), reg.customSize(), core.isEmpty() ? null : core, custom.isEmpty() ? null : custom);
        return JsonRecordSupport.toJsonObject(response);
    }
}
