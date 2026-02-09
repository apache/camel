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
import org.apache.camel.support.PluginHelper;
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

        // camel-rest is optional
        if (getCamelContext().getCamelContextExtension().isContextPluginInUse(RestRegistry.class)) {
            rr = PluginHelper.getRestRegistry(getCamelContext());
        }
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        if (rr != null) {
            for (RestRegistry.RestService rs : rr.listAllRestServices()) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(String.format("%n    Url: %s", rs.getUrl()));
                sb.append(String.format("%n    Method: %s", rs.getMethod()));
                sb.append(String.format("%n    State: %s", rs.getState()));
                if (rs.getConsumes() != null) {
                    sb.append(String.format("%n    Consumes: %s", rs.getConsumes()));
                }
                if (rs.getProduces() != null) {
                    sb.append(String.format("%n    Produces: %s", rs.getProduces()));
                }
                if (rs.getInType() != null) {
                    sb.append(String.format("%n    In Type: %s", rs.getInType()));
                }
                if (rs.getOutType() != null) {
                    sb.append(String.format("%n    Out Type: %s", rs.getOutType()));
                }
                if (rs.getDescription() != null) {
                    sb.append(String.format("%n    Description: %s", rs.getDescription()));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        if (rr != null) {
            List<JsonObject> list = new ArrayList<>();
            root.put("rests", list);

            for (RestRegistry.RestService rs : rr.listAllRestServices()) {
                JsonObject jo = new JsonObject();
                jo.put("url", rs.getUrl());
                jo.put("method", rs.getMethod());
                jo.put("contractFirst", rs.isContractFirst());
                jo.put("state", rs.getState());
                if (rs.getConsumes() != null) {
                    jo.put("consumes", rs.getConsumes());
                }
                if (rs.getProduces() != null) {
                    jo.put("produces", rs.getProduces());
                }
                if (rs.getInType() != null) {
                    jo.put("inType", rs.getInType());
                }
                if (rs.getOutType() != null) {
                    jo.put("outType", rs.getOutType());
                }
                if (rs.getDescription() != null) {
                    jo.put("description", rs.getDescription());
                }
                list.add(jo);
            }
        }

        return root;
    }

}
