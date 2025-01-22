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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsonable;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.support.MessageHelper.dumpExceptionAsJSonObject;

/**
 * An event message holding the traced message by the {@link BacklogTracer}.
 */
public final class DefaultBacklogTracerEventMessage implements BacklogTracerEventMessage {

    private final CamelContext camelContext;
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
    private boolean remoteEndpoint;
    private String endpointServiceUrl;
    private String endpointServiceProtocol;
    private Map<String, String> endpointServiceMetadata;
    private final boolean rest;
    private final boolean template;
    private final JsonObject data;
    private volatile String dataAsJson;
    private volatile String dataAsXml;
    private Throwable exception;
    private volatile JsonObject exceptionAsJsonObject;
    private volatile String exceptionAsXml;
    private volatile String exceptionAsJSon;
    private long duration;
    private boolean done;

    public DefaultBacklogTracerEventMessage(CamelContext camelContext, boolean first, boolean last, long uid, long timestamp,
                                            String location, String routeId, String toNode, String exchangeId,
                                            boolean rest, boolean template, JsonObject data) {
        this.camelContext = camelContext;
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
        this.threadName = Thread.currentThread().getName();
        this.data = data;
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
        return getMessageAsXml(4);
    }

    public String getMessageAsXml(int indent) {
        if (dataAsXml == null) {
            dataAsXml = toXML(data, indent);
        }
        return dataAsXml;
    }

    @Override
    public String getMessageAsJSon() {
        if (dataAsJson == null) {
            dataAsJson = data.toJson();
        }
        return dataAsJson;
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
        return exception != null;
    }

    @Override
    public void setException(Throwable exception) {
        this.exception = exception;
    }

    @Override
    public String getExceptionAsXml() {
        return getExceptionAsXml(4);
    }

    public String getExceptionAsXml(int indent) {
        if (exceptionAsXml == null && exception != null) {
            exceptionAsXml = MessageHelper.dumpExceptionAsXML(exception, indent);
        }
        return exceptionAsXml;
    }

    @Override
    public String getExceptionAsJSon() {
        if (exceptionAsJSon == null && exception != null) {
            exceptionAsJSon = MessageHelper.dumpExceptionAsJSon(exception, 4, true);
        }
        return exceptionAsJSon;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    @Override
    public boolean isRemoteEndpoint() {
        return remoteEndpoint;
    }

    public void setRemoteEndpoint(boolean remoteEndpoint) {
        this.remoteEndpoint = remoteEndpoint;
    }

    public void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
        // dirty flag
        this.dataAsJson = null;
        this.dataAsXml = null;
    }

    public String getEndpointServiceUrl() {
        return endpointServiceUrl;
    }

    public void setEndpointServiceUrl(String endpointServiceUrl) {
        this.endpointServiceUrl = endpointServiceUrl;
        // dirty flag
        this.dataAsJson = null;
        this.dataAsXml = null;
    }

    public String getEndpointServiceProtocol() {
        return endpointServiceProtocol;
    }

    public void setEndpointServiceProtocol(String endpointServiceProtocol) {
        this.endpointServiceProtocol = endpointServiceProtocol;
        // dirty flag
        this.dataAsJson = null;
        this.dataAsXml = null;
    }

    public Map<String, String> getEndpointServiceMetadata() {
        return endpointServiceMetadata;
    }

    public void setEndpointServiceMetadata(Map<String, String> endpointServiceMetadata) {
        this.endpointServiceMetadata = endpointServiceMetadata;
        // dirty flag
        this.dataAsJson = null;
        this.dataAsXml = null;
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
        final String prefix = " ".repeat(indent);

        StringBuilder sb = new StringBuilder(512);
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
            sb.append(prefix).append("  <remoteEndpoint>").append(remoteEndpoint).append("</remoteEndpoint>\n");
        }
        if (toNode != null) {
            sb.append(prefix).append("  <toNode>").append(toNode).append("</toNode>\n");
        } else {
            // if first message the use routeId as toNode
            sb.append(prefix).append("  <toNode>").append(routeId).append("</toNode>\n");
        }
        sb.append(prefix).append("  <exchangeId>").append(exchangeId).append("</exchangeId>\n");
        if (endpointServiceUrl != null) {
            sb.append(prefix).append("  <endpointService>\n");
            sb.append(prefix).append("    <serviceUrl>").append(endpointServiceUrl).append("</serviceUrl>\n");
            if (endpointServiceProtocol != null) {
                sb.append(prefix).append("    <serviceProtocol>").append(endpointServiceProtocol)
                        .append("</serviceProtocol>\n");
            }
            if (endpointServiceMetadata != null) {
                sb.append(prefix).append("    <serviceMetadata>\n");
                endpointServiceMetadata.forEach((k, v) -> {
                    sb.append(prefix).append("      <").append(k).append(">").append(v).append("</").append(k).append(">\n");
                });
                sb.append(prefix).append("    </serviceMetadata>\n");
            }
            sb.append(prefix).append("  </endpointService>\n");
        }
        sb.append(getMessageAsXml(indent + 2)).append("\n");
        if (getExceptionAsXml() != null) {
            sb.append(prefix).append(getExceptionAsXml(indent + 2)).append("\n");
        }
        sb.append(prefix).append("</").append(ROOT_TAG).append(">");
        return sb.toString();
    }

    private String toXML(JsonObject data, int indent) {
        StringBuilder sb = new StringBuilder(1024);

        final String prefix = " ".repeat(indent);

        JsonObject root = data.getMap("message");

        // include exchangeId/exchangePattern/type as attribute on the <message> tag
        sb.append(prefix);
        sb.append("<message exchangeId=\"").append(root.getString("exchangeId"))
                .append("\" exchangePattern=\"").append(root.getString("exchangePattern"))
                .append("\" exchangeType=\"").append(root.getString("exchangeType"))
                .append("\" messageType=\"").append(root.getString("messageType")).append("\">\n");

        // exchange variables
        JsonArray arr = root.getCollection("exchangeVariables");
        if (arr != null && !arr.isEmpty()) {
            sb.append(prefix);
            sb.append("  <exchangeVariables>\n");
            for (var entry : arr) {
                JsonObject jo = (JsonObject) entry;
                sb.append(prefix);
                sb.append("    <exchangeVariable key=\"").append(jo.getString("key")).append("\"");
                String type = jo.getString("type");
                if (type != null) {
                    sb.append(" type=\"").append(type).append("\"");
                }
                sb.append(">");
                Object value = jo.get("value");
                if (value != null) {
                    try {
                        String text = camelContext.getTypeConverter().tryConvertTo(String.class, value);
                        // must always xml encode
                        sb.append(StringHelper.xmlEncode(text));
                    } catch (Exception e) {
                        // ignore
                    }
                }
                sb.append("</exchangeVariable>\n");
            }
            sb.append(prefix);
            sb.append("  </exchangeVariables>\n");
        }
        // exchange properties
        arr = root.getCollection("exchangeProperties");
        if (arr != null && !arr.isEmpty()) {
            sb.append(prefix);
            sb.append("  <exchangeProperties>\n");
            for (var entry : arr) {
                JsonObject jo = (JsonObject) entry;
                sb.append(prefix);
                sb.append("    <exchangeProperty key=\"").append(jo.getString("key")).append("\"");
                String type = jo.getString("type");
                if (type != null) {
                    sb.append(" type=\"").append(type).append("\"");
                }
                sb.append(">");
                Object value = jo.get("value");
                if (value != null) {
                    try {
                        String text = camelContext.getTypeConverter().tryConvertTo(String.class, value);
                        // must always xml encode
                        sb.append(StringHelper.xmlEncode(text));
                    } catch (Exception e) {
                        // ignore
                    }
                }
                sb.append("</exchangeProperty>\n");
            }
            sb.append(prefix);
            sb.append("  </exchangeProperties>\n");
        }
        // headers
        arr = root.getCollection("headers");
        if (arr != null && !arr.isEmpty()) {
            sb.append(prefix);
            sb.append("  <headers>\n");
            for (var entry : arr) {
                JsonObject jo = (JsonObject) entry;
                sb.append(prefix);
                sb.append("    <header key=\"").append(jo.getString("key")).append("\"");
                String type = jo.getString("type");
                if (type != null) {
                    sb.append(" type=\"").append(type).append("\"");
                }
                sb.append(">");
                Object value = jo.get("value");
                if (value != null) {
                    try {
                        String text = camelContext.getTypeConverter().tryConvertTo(String.class, value);
                        // must always xml encode
                        sb.append(StringHelper.xmlEncode(text));
                    } catch (Exception e) {
                        // ignore
                    }
                }
                sb.append("</header>\n");
            }
            sb.append(prefix);
            sb.append("  </headers>\n");
        }
        JsonObject jo = root.getMap("body");
        if (jo != null) {
            sb.append(prefix);
            sb.append("  <body");
            String type = jo.getString("type");
            if (type != null) {
                sb.append(" type=\"").append(type).append("\"");
            }
            Long size = jo.getLong("size");
            if (size != null) {
                sb.append(" size=\"").append(size).append("\"");
            }
            Long position = jo.getLong("position");
            if (position != null) {
                sb.append(" position=\"").append(position).append("\"");
            }
            sb.append(">");
            Object value = jo.get("value");
            if (value != null) {
                try {
                    String text = camelContext.getTypeConverter().tryConvertTo(String.class, value);
                    // must always xml encode
                    sb.append(StringHelper.xmlEncode(text));
                } catch (Exception e) {
                    // ignore
                }
            }
            sb.append("</body>\n");
        }

        sb.append(prefix);
        sb.append("</message>");
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
            jo.put("remoteEndpoint", remoteEndpoint);
        }
        if (routeId != null) {
            jo.put("routeId", routeId);
        }
        if (toNode != null) {
            jo.put("nodeId", toNode);
        }
        if (exchangeId != null) {
            jo.put("exchangeId", exchangeId);
        }
        if (timestamp > 0) {
            jo.put("timestamp", timestamp);
        }
        jo.put("elapsed", getElapsed());
        jo.put("threadName", getProcessingThreadName());
        jo.put("done", isDone());
        jo.put("failed", isFailed());
        if (endpointServiceUrl != null) {
            JsonObject es = new JsonObject();
            es.put("serviceUrl", endpointServiceUrl);
            if (endpointServiceProtocol != null) {
                es.put("serviceProtocol", endpointServiceProtocol);
            }
            if (endpointServiceMetadata != null) {
                es.put("serviceMetadata", endpointServiceMetadata);
            }
            jo.put("endpointService", es);
        }
        jo.put("message", data.getMap("message"));
        if (exception != null) {
            if (exceptionAsJsonObject == null) {
                try {
                    exceptionAsJsonObject = dumpExceptionAsJSonObject(exception);
                } catch (Exception e) {
                    // ignore
                }
            }
            if (exceptionAsJsonObject != null) {
                jo.put("exception", exceptionAsJsonObject.get("exception"));
            }
        }
        return jo;
    }
}
