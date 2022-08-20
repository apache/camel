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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("endpoints")
public class EndpointsDevConsole extends AbstractDevConsole {

    public EndpointsDevConsole() {
        super("camel", "endpoints", "Endpoints", "Endpoint Registry information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        EndpointRegistry reg = getCamelContext().getEndpointRegistry();
        sb.append(
                String.format("\n    Endpoints: %s (static: %s dynamic: %s", reg.size(), reg.staticSize(), reg.dynamicSize()));
        sb.append(String.format("\n    Maximum Cache Size: %s", reg.getMaximumCacheSize()));
        Collection<Endpoint> col = reg.getReadOnlyValues();
        if (!col.isEmpty()) {
            sb.append("\n");
            for (Endpoint e : col) {
                sb.append(String.format("\n    %s", e.toString()));
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        EndpointRegistry reg = getCamelContext().getEndpointRegistry();
        root.put("size", reg.size());
        root.put("staticSize", reg.staticSize());
        root.put("dynamicSize", reg.dynamicSize());
        root.put("maximumCacheSize", reg.getMaximumCacheSize());

        final List<JsonObject> list = new ArrayList<>();
        root.put("endpoints", list);
        Collection<Endpoint> col = reg.getReadOnlyValues();
        for (Endpoint e : col) {
            JsonObject uri = new JsonObject();
            uri.put("uri", e.toString());
            list.add(uri);
        }

        return root;
    }
}
