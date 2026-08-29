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

import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerActivityMessage;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "activity", displayName = "Camel Activity", description = "Recent completed exchange activity")
public class ActivityDevConsole extends AbstractDevConsole {

    public record EndpointSend(
            @Metadata(description = "The endpoint URI") String endpointUri,
            @Metadata(description = "Whether the endpoint is remote") boolean remoteEndpoint,
            @Metadata(description = "Elapsed time in milliseconds") long elapsed) {
    }

    public record ActivityEntry(
            @Metadata(description = "Unique ID of the activity entry") long uid,
            @Metadata(description = "The exchange ID") String exchangeId,
            @Metadata(description = "The route ID (only present when known)") String routeId,
            @Metadata(description = "The from endpoint URI (only present when known)") String fromEndpointUri,
            @Metadata(description = "Epoch time in milliseconds (only present when known)") Long timestamp,
            @Metadata(description = "Elapsed time in milliseconds") long elapsed,
            @Metadata(description = "Whether the exchange failed") boolean failed,
            @Metadata(description = "The exception message (only present when the exchange failed)") String exception,
            @Metadata(description = "The endpoints this exchange was sent to (only present when any)") List<EndpointSend> endpointSends) {
    }

    public record Response(
            @Metadata(description = "Whether activity tracking is enabled") Boolean activityEnabled,
            @Metadata(description = "The maximum number of activity entries retained") Integer activitySize,
            @Metadata(description = "The recent activity entries") List<ActivityEntry> activity) {
    }

    public ActivityDevConsole() {
        super("camel", "activity", "Camel Activity", "Recent completed exchange activity");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            sb.append("Activity Enabled: ").append(tracer.isActivityEnabled()).append("\n");
            sb.append("Activity Size: ").append(tracer.getActivitySize()).append("\n");
            for (BacklogTracerActivityMessage event : tracer.getActivity()) {
                sb.append(String.format("  %s | %s | %s | %dms | %s",
                        event.getExchangeId(),
                        event.getRouteId(),
                        event.getFromEndpointUri() != null ? event.getFromEndpointUri() : "",
                        event.getElapsed(),
                        event.isFailed() ? "FAILED" : "OK"));
                List<BacklogTracerActivityMessage.EndpointSend> sends = event.getEndpointSends();
                if (sends != null && !sends.isEmpty()) {
                    sb.append(" | sent: ").append(sends.size());
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);

        Response response;
        if (tracer != null) {
            List<ActivityEntry> activity = new ArrayList<>();
            for (BacklogTracerActivityMessage event : tracer.getActivity()) {
                List<EndpointSend> sends = null;
                List<BacklogTracerActivityMessage.EndpointSend> eventSends = event.getEndpointSends();
                if (eventSends != null && !eventSends.isEmpty()) {
                    sends = new ArrayList<>();
                    for (BacklogTracerActivityMessage.EndpointSend send : eventSends) {
                        sends.add(new EndpointSend(send.getEndpointUri(), send.isRemoteEndpoint(), send.getElapsed()));
                    }
                }
                Long timestamp = event.getTimestamp() > 0 ? event.getTimestamp() : null;
                activity.add(new ActivityEntry(
                        event.getUid(), event.getExchangeId(), event.getRouteId(), event.getFromEndpointUri(), timestamp,
                        event.getElapsed(), event.isFailed(), event.getExceptionMessage(), sends));
            }
            response = new Response(tracer.isActivityEnabled(), tracer.getActivitySize(), activity);
        } else {
            response = new Response(null, null, null);
        }

        return JsonRecordSupport.toJsonObject(response);
    }

}
