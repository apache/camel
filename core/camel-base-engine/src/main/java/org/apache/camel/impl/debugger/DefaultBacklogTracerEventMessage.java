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
package org.apache.camel.impl.debugger;

import java.text.SimpleDateFormat;
import java.util.Map;

import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsonable;
import org.apache.camel.util.json.Jsoner;

/**
 * An event message holding the traced message by the {@link BacklogTracer}.
 */
public final class DefaultBacklogTracerEventMessage implements BacklogTracerEventMessage {

    private final long uid;
    private final long timestamp;
    private final String routeId;
    private final String toNode;
    private final String exchangeId;
    private final String messageAsXml;
    private final String messageAsJSon;

    public DefaultBacklogTracerEventMessage(long uid, long timestamp, String routeId, String toNode, String exchangeId,
                                            String messageAsXml, String messageAsJSon) {
        this.uid = uid;
        this.timestamp = timestamp;
        this.routeId = routeId;
        this.toNode = toNode;
        this.exchangeId = exchangeId;
        this.messageAsXml = messageAsXml;
        this.messageAsJSon = messageAsJSon;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public String getToNode() {
        return toNode;
    }

    @Override
    public String getExchangeId() {
        return exchangeId;
    }

    @Override
    public String getMessageAsXml() {
        return messageAsXml;
    }

    @Override
    public String getMessageAsJSon() {
        return messageAsJSon;
    }

    @Override
    public String toString() {
        return "DefaultBacklogTracerEventMessage[" + exchangeId + " at " + toNode + "]";
    }

    /**
     * Dumps the event message as XML using the {@link #ROOT_TAG} as root tag.
     * <p/>
     * The <tt>timestamp</tt> tag is formatted in the format defined by {@link #TIMESTAMP_FORMAT}
     *
     * @return xml representation of this event
     */
    @Override
    public String toXml(int indent) {
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            prefix.append(" ");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append("<").append(ROOT_TAG).append(">\n");
        sb.append(prefix).append("  <uid>").append(uid).append("</uid>\n");
        String ts = new SimpleDateFormat(TIMESTAMP_FORMAT).format(timestamp);
        sb.append(prefix).append("  <timestamp>").append(ts).append("</timestamp>\n");
        // route id is optional and we then use an empty value for no route id
        sb.append(prefix).append("  <routeId>").append(routeId != null ? routeId : "").append("</routeId>\n");
        if (toNode != null) {
            sb.append(prefix).append("  <toNode>").append(toNode).append("</toNode>\n");
        } else {
            // if first message the use routeId as toNode
            sb.append(prefix).append("  <toNode>").append(routeId).append("</toNode>\n");
        }
        sb.append(prefix).append("  <exchangeId>").append(exchangeId).append("</exchangeId>\n");
        sb.append(prefix).append(messageAsXml).append("\n");
        sb.append(prefix).append("</").append(ROOT_TAG).append(">");
        return sb.toString();
    }

    @Override
    public String toJSon(int indent) {
        Jsonable jo = (Jsonable) asJSon();
        if (indent > 0) {
            return Jsoner.prettyPrint(jo.toJson(), indent);
        } else {
            return Jsoner.prettyPrint(jo.toJson());
        }
    }

    @Override
    public Map<String, Object> asJSon() {
        JsonObject jo = new JsonObject();
        jo.put("uid", uid);
        if (routeId != null) {
            jo.put("routeId", routeId);
        }
        if (toNode != null) {
            jo.put("nodeId", toNode);
        }
        if (timestamp > 0) {
            jo.put("timestamp", timestamp);
        }
        try {
            // parse back to json object and avoid double message root
            JsonObject msg = (JsonObject) Jsoner.deserialize(messageAsJSon);
            jo.put("message", msg.get("message"));
        } catch (Exception e) {
            // ignore
        }
        return jo;
    }
}
