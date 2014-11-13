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
package org.apache.camel.commands;

import java.io.PrintStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.util.MessageDump;

public class BacklogTracerDumpCommand extends AbstractContextCommand {

    private String pattern;
    private String format;
    private Integer bodySize;

    /**
     * @param context  The name of the Camel context.
     * @param pattern  To dump trace messages only for nodes or routes matching the given pattern (default is all)
     * @param format   Format to use with the dump action (text or xml)
     * @param bodySize To limit the body size when using text format
     */
    public BacklogTracerDumpCommand(String context, String pattern, String format, Integer bodySize) {
        super(context);
        this.pattern = pattern;
        this.format = format;
        this.bodySize = bodySize;
    }

    @Override
    protected Object performContextCommand(CamelController camelController, CamelContext camelContext, PrintStream out, PrintStream err) throws Exception {
        BacklogTracer backlogTracer = BacklogTracer.getBacklogTracer(camelContext);
        if (backlogTracer == null) {
            backlogTracer = (BacklogTracer) camelContext.getDefaultBacklogTracer();
        }

        if (format == null || "text".equals(format)) {
            JAXBContext context = JAXBContext.newInstance(MessageDump.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            SimpleDateFormat sdf = new SimpleDateFormat(BacklogTracerEventMessage.TIMESTAMP_FORMAT);

            List<BacklogTracerEventMessage> events;
            if (pattern != null) {
                events = backlogTracer.dumpTracedMessages(pattern);
            } else {
                events = backlogTracer.dumpAllTracedMessages();
            }
            for (BacklogTracerEventMessage event : events) {
                MessageDump msg = (MessageDump) unmarshaller.unmarshal(new StringReader(event.getMessageAsXml()));
                String breadcrumb = getBreadcrumbId(msg.getHeaders());

                out.println("#" + event.getUid() + "\tTimestamp:\t" + sdf.format(event.getTimestamp()));
                if (breadcrumb != null) {
                    out.println("Breadcrumb: " + breadcrumb);
                }
                out.println("ExchangeId: " + event.getExchangeId());

                if (event.getToNode() != null) {
                    out.println("Route: " + event.getRouteId() + "\t--> " + event.getToNode());
                } else {
                    out.println("Route: " + event.getRouteId());
                }

                String body = msg.getBody().getValue();
                if (bodySize != null && bodySize > 0) {
                    if (body.length() > bodySize) {
                        body = body.substring(0, bodySize);
                    }
                }
                out.println(body);
                out.println("");
            }
        } else if ("xml".equals(format)) {
            if (pattern != null) {
                out.println("BacklogTracer messages:\n" + backlogTracer.dumpTracedMessages(pattern));
            } else {
                out.println("BacklogTracer messages:\n" + backlogTracer.dumpAllTracedMessagesAsXml());
            }
            return null;
        }

        return null;
    }

    private static String getBreadcrumbId(List<MessageDump.Header> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (MessageDump.Header header : headers) {
            if (header.getKey().equals(Exchange.BREADCRUMB_ID)) {
                return header.getValue();
            }
        }
        return null;
    }

}
