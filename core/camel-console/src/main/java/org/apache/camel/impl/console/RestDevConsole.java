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

import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "rest", displayName = "Rest", description = "Rest DSL Registry information")
public class RestDevConsole extends AbstractDevConsole {

    public RestDevConsole() {
        super("camel", "rest", "Rest", "Rest DSL Registry information");
    }

    private RestRegistry rr;

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        try {
            rr = getCamelContext().getRestRegistry();
        } catch (IllegalArgumentException e) {
            // ignore as this is optional
        }
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        if (rr == null) {
            return sb.toString();
        }

        for (RestRegistry.RestService rs : rr.listAllRestServices()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            appendRestServiceText(sb, rs);
        }
        sb.append("\n");

        return sb.toString();
    }

    private void appendRestServiceText(StringBuilder sb, RestRegistry.RestService rs) {
        sb.append(String.format("%n    Url: %s", rs.getUrl()));
        sb.append(String.format("%n    Method: %s", rs.getMethod()));
        sb.append(String.format("%n    State: %s", rs.getState()));
        appendOptionalText(sb, "Consumes", rs.getConsumes());
        appendOptionalText(sb, "Produces", rs.getProduces());
        appendOptionalText(sb, "In Type", rs.getInType());
        appendOptionalText(sb, "Out Type", rs.getOutType());
        appendOptionalText(sb, "Description", rs.getDescription());
    }

    private void appendOptionalText(StringBuilder sb, String label, String value) {
        if (value != null) {
            sb.append(String.format("%n    %s: %s", label, value));
        }
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        if (rr == null) {
            return root;
        }

        List<JsonObject> list = new ArrayList<>();
        root.put("rests", list);

        for (RestRegistry.RestService rs : rr.listAllRestServices()) {
            list.add(buildRestServiceJson(rs));
        }

        return root;
    }

    private JsonObject buildRestServiceJson(RestRegistry.RestService rs) {
        JsonObject jo = new JsonObject();
        jo.put("url", rs.getUrl());
        jo.put("method", rs.getMethod());
        jo.put("contractFirst", rs.isContractFirst());
        jo.put("state", rs.getState());
        putIfNotNull(jo, "consumes", rs.getConsumes());
        putIfNotNull(jo, "produces", rs.getProduces());
        putIfNotNull(jo, "inType", rs.getInType());
        putIfNotNull(jo, "outType", rs.getOutType());
        putIfNotNull(jo, "description", rs.getDescription());
        return jo;
    }

    private void putIfNotNull(JsonObject jo, String key, String value) {
        if (value != null) {
            jo.put(key, value);
        }
    }

}
