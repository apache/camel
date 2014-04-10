/**
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
package org.apache.camel.processor.interceptor;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;

/**
 * An event message holding the traced message by the {@link BacklogTracer}.
 */
public final class DefaultBacklogTracerEventMessage implements BacklogTracerEventMessage {

    private static final long serialVersionUID = 1L;

    private final long uid;
    private final Date timestamp;
    private final String routeId;
    private final String toNode;
    private final String exchangeId;
    private final String messageAsXml;

    public DefaultBacklogTracerEventMessage(long uid, Date timestamp, String routeId, String toNode, String exchangeId, String messageAsXml) {
        this.uid = uid;
        this.timestamp = timestamp;
        this.routeId = routeId;
        this.toNode = toNode;
        this.exchangeId = exchangeId;
        this.messageAsXml = messageAsXml;
    }

    public long getUid() {
        return uid;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getToNode() {
        return toNode;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public String getMessageAsXml() {
        return messageAsXml;
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
}

