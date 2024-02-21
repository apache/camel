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
package org.apache.camel.component.platform.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("platform-http")
public class PlatformHttpConsole extends AbstractDevConsole {

    public PlatformHttpConsole() {
        super("camel", "platform-http", "Platform HTTP", "Embedded HTTP Server");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        PlatformHttpComponent http = getCamelContext().getComponent("platform-http", PlatformHttpComponent.class);
        if (http != null) {
            String server = "http://0.0.0.0";
            int port = http.getEngine().getServerPort();
            if (port > 0) {
                server += ":" + port;
            }
            Set<HttpEndpointModel> models = http.getHttpEndpoints();
            for (HttpEndpointModel model : models) {
                if (model.getVerbs() != null) {
                    sb.append(String.format("    %s%s (%s)\n", server, model.getUri(), model.getVerbs()));
                } else {
                    sb.append(String.format("    %s%s\n", server, model.getUri()));
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        PlatformHttpComponent http = getCamelContext().getComponent("platform-http", PlatformHttpComponent.class);
        if (http != null) {
            String server = "http://0.0.0.0";
            int port = http.getEngine().getServerPort();
            if (port > 0) {
                server += ":" + port;
            }
            root.put("server", server);

            final List<JsonObject> list = buildEndpointList(http, server);
            if (!list.isEmpty()) {
                root.put("endpoints", list);
            }
        }

        return root;
    }

    private static List<JsonObject> buildEndpointList(PlatformHttpComponent http, String server) {
        Set<HttpEndpointModel> models = http.getHttpEndpoints();
        List<JsonObject> list = new ArrayList<>();
        for (HttpEndpointModel model : models) {
            JsonObject jo = new JsonObject();
            String uri = model.getUri();
            if (!uri.startsWith("/")) {
                uri = "/" + uri;
            }
            jo.put("url", server + uri);
            jo.put("path", model.getUri());
            if (model.getVerbs() != null) {
                jo.put("verbs", model.getVerbs());
            }
            list.add(jo);
        }
        return list;
    }
}
