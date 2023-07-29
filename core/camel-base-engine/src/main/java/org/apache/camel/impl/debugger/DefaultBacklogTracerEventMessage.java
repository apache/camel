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
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsonable;
import org.apache.camel.util.json.Jsoner;

/**
 * An event message holding the traced message by the {@link BacklogTracer}.
 */
public final class DefaultBacklogTracerEventMessage implements BacklogTracerEventMessage {

    private final StopWatch watch;
    private final boolean first;
    private final boolean last;
    private final long uid;
    private final long timestamp;
    private final String location;
    private final String routeId;
    private final String toNode;
    private final String exchangeId;
    private final String threadName;
    private String endpointUri;
    private final boolean rest;
    private final boolean template;
    private final String messageAsXml;
    private final String messageAsJSon;
    private String exceptionAsXml;
    private String exceptionAsJSon;
    private long duration;
    private boolean done;

    public DefaultBacklogTracerEventMessage(boolean first, boolean last, long uid, long timestamp,
                                            String location, String routeId, String toNode, String exchangeId,
                                            boolean rest, boolean template,
                                            String messageAsXml, String messageAsJSon) {
        this.watch = new StopWatch();
        this.first = first;
        this.last = last;
        this.uid = uid;
        this.timestamp = timestamp;
        this.location = location;
        this.routeId = routeId;
        this.toNode = toNode;
        this.exchangeId = exchangeId;
        this.rest = rest;
        this.template = template;
        this.messageAsXml = messageAsXml;
        this.messageAsJSon = messageAsJSon;
        this.threadName = Thread.currentThread().getName();
    }

    /**
     * Callback when the message has been processed at the given node
     */
    public void doneProcessing() {
        this.done = true;
        this.duration = watch.taken();
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public boolean isFirst() {
        return first;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public boolean isRest() {
        return rest;
    }

    @Override
    public boolean isTemplate() {
        return template;
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
    public String getProcessingThreadName() {
        return threadName;
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
    public long getElapsed() {
        return done ? duration : watch.taken();
    }

    public void setElapsed(long elapsed) {
        this.duration = elapsed;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    public boolean isFailed() {
        return hasException();
    }

    @Override
    public boolean hasException() {
        return exceptionAsXml != null || exceptionAsJSon != null;
    }

    @Override
    public String getExceptionAsXml() {
        return exceptionAsXml;
    }

    public void setExceptionAsXml(String exceptionAsXml) {
        this.exceptionAsXml = exceptionAsXml;
    }

    @Override
    public String getExceptionAsJSon() {
        return exceptionAsJSon;
    }

    public void setExceptionAsJSon(String exceptionAsJSon) {
        this.exceptionAsJSon = exceptionAsJSon;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
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
        prefix.append(" ".repeat(indent));

        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append("<").append(ROOT_TAG).append(">\n");
        sb.append(prefix).append("  <uid>").append(uid).append("</uid>\n");
        sb.append(prefix).append("  <first>").append(first).append("</first>\n");
        sb.append(prefix).append("  <last>").append(last).append("</last>\n");
        sb.append(prefix).append("  <rest>").append(rest).append("</rest>\n");
        sb.append(prefix).append("  <template>").append(template).append("</template>\n");
        String ts = new SimpleDateFormat(TIMESTAMP_FORMAT).format(timestamp);
        sb.append(prefix).append("  <timestamp>").append(ts).append("</timestamp>\n");
        sb.append(prefix).append("  <elapsed>").append(getElapsed()).append("</elapsed>\n");
        sb.append(prefix).append("  <threadName>").append(getProcessingThreadName()).append("</threadName>\n");
        sb.append(prefix).append("  <done>").append(isDone()).append("</done>\n");
        sb.append(prefix).append("  <failed>").append(isFailed()).append("</failed>\n");
        if (getLocation() != null) {
            sb.append(prefix).append("  <location>").append(getLocation()).append("</location>\n");
        }
        // route id is optional and we then use an empty value for no route id
        sb.append(prefix).append("  <routeId>").append(routeId != null ? routeId : "").append("</routeId>\n");
        if (endpointUri != null) {
            sb.append(prefix).append("  <endpointUri>").append(endpointUri).append("</endpointUri>\n");
        }
        if (toNode != null) {
            sb.append(prefix).append("  <toNode>").append(toNode).append("</toNode>\n");
        } else {
            // if first message the use routeId as toNode
            sb.append(prefix).append("  <toNode>").append(routeId).append("</toNode>\n");
        }
        sb.append(prefix).append("  <exchangeId>").append(exchangeId).append("</exchangeId>\n");
        sb.append(prefix).append(messageAsXml).append("\n");
        if (exceptionAsXml != null) {
            sb.append(prefix).append(exceptionAsXml).append("\n");
        }
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
        jo.put("first", first);
        jo.put("last", last);
        jo.put("rest", rest);
        jo.put("template", template);
        if (location != null) {
            jo.put("location", location);
        }
        if (endpointUri != null) {
            jo.put("endpointUri", endpointUri);
        }
        if (routeId != null) {
            jo.put("routeId", routeId);
        }
        if (toNode != null) {
            jo.put("nodeId", toNode);
        }
        if (timestamp > 0) {
            jo.put("timestamp", timestamp);
        }
        jo.put("elapsed", getElapsed());
        jo.put("threadName", getProcessingThreadName());
        jo.put("done", isDone());
        jo.put("failed", isFailed());
        try {
            // parse back to json object and avoid double message root
            JsonObject msg = (JsonObject) Jsoner.deserialize(messageAsJSon);
            msg = msg.getMap("message");
            jo.put("message", msg);

            // [Body is null] -> null
            msg = msg.getMap("body");
            Object body = msg.get("value");
            if ("[Body is null]".equals(body)) {
                msg.replace("value", null);
            }
        } catch (Exception e) {
            // ignore
        }
        if (exceptionAsJSon != null) {
            try {
                // parse back to json object and avoid double message root
                JsonObject msg = (JsonObject) Jsoner.deserialize(exceptionAsJSon);
                jo.put("exception", msg.get("exception"));
            } catch (Exception e) {
                // ignore
            }
        }
        return jo;
    }
}
