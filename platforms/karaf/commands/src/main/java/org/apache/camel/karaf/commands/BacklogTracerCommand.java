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
import org.apache.karaf.shell.console.OsgiCommandSupport;

/**
 * Command to use the <a href="camel.apache.org/backlogtracer">Backlog Tracer</a>.
 */
@Command(scope = "camel", name = "backlog-tracer", description = "Access the Backlog tracer")
public class BacklogTracerCommand extends OsgiCommandSupport {

    @Option(name ="-a", aliases = {"--action"}, description = "The action to perform.", required = true, multiValued = false)
    String action;

    @Argument(index = 0, name = "context", description = "The name of the Camel context.", required = true, multiValued = false)
    String context;

    @Argument(index = 1, name = "nodeId", description = "To dump trace messages only for the given node id (default is all)", required = false, multiValued = false)
    String route;

    @Argument(index = 2, name = "format", description = "Format to use with the dump action (default is xml)", required = false, multiValued = false)
    String format;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

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

        if ("enable".equals(action)) {
            backlogTracer.setEnabled(true);
            System.out.println("BacklogTracer enabled on " + camel.getName());
            return null;
        } else if ("disable".equals(action)) {
            backlogTracer.setEnabled(false);
            System.out.println("BacklogTracer disabled on " + camel.getName());
            return null;
        } else if ("summary".equals(action)) {
            System.out.println("BacklogTracer context:" + camel.getName());
            System.out.println("BacklogTracer enabled:" + backlogTracer.isEnabled());
            System.out.println("BacklogTracer backlogSize:" + backlogTracer.getBacklogSize());
            System.out.println("BacklogTracer tracerCount:" + backlogTracer.getTraceCounter());
            return null;
        } else if ("dump".equals(action)) {
            if (format == null || "xml".equals(format)) {
                System.out.println("--------------------------------------------------------");
                System.out.println("BacklogTracer messages:\n" + backlogTracer.dumpAllTracedMessagesAsXml());
                return null;
            } else if ("table".equals(format)) {
                JAXBContext context = JAXBContext.newInstance(MessageDump.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();

                // assume its a table
                SimpleDateFormat sdf = new SimpleDateFormat(BacklogTracerEventMessage.TIMESTAMP_FORMAT);
                List<BacklogTracerEventMessage> events = backlogTracer.dumpAllTracedMessages();
                for (BacklogTracerEventMessage event : events) {
                    System.out.println("--------------------------------------------------------");
                    System.out.println("#" + event.getUid() + "\tExchangeId: " + event.getExchangeId());
                    System.out.println("Timestamp: " + sdf.format(event.getTimestamp()));
                    System.out.println("Route: " + event.getRouteId() + " --> " + event.getToNode());

                    MessageDump msg = (MessageDump) unmarshaller.unmarshal(new StringReader(event.getMessageAsXml()));
                    String breadcrumb = getBreadcrumbId(msg.getHeaders());
                    if (breadcrumb != null) {
                        System.out.println("Breadcrumb: " + breadcrumb);
                    }
                    System.out.println("Body: " + msg.getBody().getValue());
                }
            }
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
