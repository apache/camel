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

import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "activity", displayName = "Camel Activity", description = "Recent completed exchange activity")
public class ActivityDevConsole extends AbstractDevConsole {

    public ActivityDevConsole() {
        super("camel", "activity", "Camel Activity", "Recent completed exchange activity");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            sb.append("Activity Size: ").append(tracer.getActivitySize()).append("\n");
            for (BacklogTracerEventMessage event : tracer.getActivity()) {
                sb.append(String.format("  %s | %s | %s | %dms | %s%n",
                        event.getExchangeId(),
                        event.getRouteId(),
                        event.getEndpointUri() != null ? event.getEndpointUri() : "",
                        event.getElapsed(),
                        event.isFailed() ? "FAILED" : "OK"));
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            root.put("activitySize", tracer.getActivitySize());

            JsonArray arr = new JsonArray();
            root.put("activity", arr);
            for (BacklogTracerEventMessage event : tracer.getActivity()) {
                JsonObject jo = new JsonObject();
                jo.put("uid", event.getUid());
                jo.put("exchangeId", event.getExchangeId());
                jo.put("routeId", event.getRouteId());
                if (event.getFromRouteId() != null) {
                    jo.put("fromRouteId", event.getFromRouteId());
                }
                if (event.getTimestamp() > 0) {
                    jo.put("timestamp", event.getTimestamp());
                }
                jo.put("elapsed", event.getElapsed());
                jo.put("failed", event.isFailed());
                if (event.getEndpointUri() != null) {
                    jo.put("endpointUri", event.getEndpointUri());
                }
                if (event.isRemoteEndpoint()) {
                    jo.put("remoteEndpoint", true);
                }
                if (event.hasException()) {
                    jo.put("exception", event.getExceptionAsJSon());
                }
                arr.add(jo);
            }
        }

        return root;
    }

}
