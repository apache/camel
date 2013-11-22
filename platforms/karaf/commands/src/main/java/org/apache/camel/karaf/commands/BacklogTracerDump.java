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
package org.apache.camel.karaf.commands;

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
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

/**
 * Command to use the <a href="camel.apache.org/backlogtracer">Backlog Tracer</a>.
 */
@Command(scope = "camel", name = "backlog-tracer-dump", description = "Dumps traced messages from the Backlog tracer")
public class BacklogTracerDump extends CamelCommandSupport {

    @Argument(index = 0, name = "context", description = "The name of the Camel context.", required = true, multiValued = false)
    String context;

    @Argument(index = 1, name = "pattern", description = "To dump trace messages only for nodes or routes matching the given pattern (default is all)", required = false, multiValued = false)
    String pattern;

    @Option(name = "--format", aliases = "-f", description = "Format to use with the dump action (text or xml)", required = false, multiValued = false, valueToShowInHelp = "text")
    String format;

    @Option(name = "--bodySize", aliases = "-bs", description = "To limit the body size when using text format", required = false, multiValued = false)
    Integer bodySize;

    @Override
    protected Object doExecute() throws Exception {
        CamelContext camel = camelController.getCamelContext(context);
        if (camel == null) {
            System.err.println("CamelContext " + context + " not found.");
            return null;
        }

        BacklogTracer backlogTracer = BacklogTracer.getBacklogTracer(camel);
        if (backlogTracer == null) {
            backlogTracer = (BacklogTracer) camel.getDefaultBacklogTracer();
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

                System.out.println("#" + event.getUid() + "\tTimestamp:\t" + sdf.format(event.getTimestamp()));
                if (breadcrumb != null) {
                    System.out.println("Breadcrumb: " + breadcrumb);
                }
                System.out.println("ExchangeId: " + event.getExchangeId());

                if (event.getToNode() != null) {
                    System.out.println("Route: " + event.getRouteId() + "\t--> " + event.getToNode());
                } else {
                    System.out.println("Route: " + event.getRouteId());
                }

                String body = msg.getBody().getValue();
                if (bodySize != null && bodySize > 0) {
                    if (body.length() > bodySize) {
                        body = body.substring(0, bodySize);
                    }
                }
                System.out.println(body);
                System.out.println("");
            }
        } else if ("xml".equals(format)) {
            if (pattern != null) {
                System.out.println("BacklogTracer messages:\n" + backlogTracer.dumpTracedMessages(pattern));
            } else {
                System.out.println("BacklogTracer messages:\n" + backlogTracer.dumpAllTracedMessagesAsXml());
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
