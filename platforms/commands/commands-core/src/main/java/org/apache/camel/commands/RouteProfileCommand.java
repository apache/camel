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
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.util.ProcessorStatDump;
import org.apache.camel.util.RouteStatDump;

public class RouteProfileCommand extends AbstractRouteCommand {

    protected static final String HEADER_FORMAT = "%-30s %10s %12s %12s %12s %12s %12s %12s %12s";
    protected static final String OUTPUT_FORMAT = "%-30s %10d %12d %12d %12d %12d %12d %12d %12d";

    private StringEscape stringEscape;
    private volatile String previousCamelContextName;

    public RouteProfileCommand(String route, String context) {
        super(route, context);
    }

    /**
     * Sets the {@link org.apache.camel.commands.StringEscape} to use.
     */
    public void setStringEscape(StringEscape stringEscape) {
        this.stringEscape = stringEscape;
    }

    @Override
    public void executeOnRoute(CamelController camelController, CamelContext camelContext, Route camelRoute, PrintStream out, PrintStream err) throws Exception {
        JAXBContext context = JAXBContext.newInstance(RouteStatDump.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        // write new header for new camel context
        if (previousCamelContextName == null || !previousCamelContextName.equals(camelContext.getName())) {
            System.out.println("");
            System.out.println(stringEscape.unescapeJava("\u001B[1mProfile\u001B[0m"));
            System.out.println(stringEscape.unescapeJava("\tCamel Context: " + camelRoute.getRouteContext().getCamelContext().getName()));
            System.out.println(String.format(HEADER_FORMAT, "Id", "Count", "Last (ms)", "Delta (ms)", "Mean (ms)", "Min (ms)", "Max (ms)", "Total (ms)", "Self (ms)"));
        }

        ManagementAgent agent = camelContext.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();
            Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(agent.getMBeanObjectDomainName() + ":type=routes,name=\"" + camelRoute.getId() + "\",*"), null);
            for (ObjectName routeMBean : set) {
                // the route must be part of the camel context
                String camelId = (String) mBeanServer.getAttribute(routeMBean, "CamelId");
                if (camelId != null && camelId.equals(camelContext.getName())) {

                    String xml = (String) mBeanServer.invoke(routeMBean, "dumpRouteStatsAsXml", new Object[]{Boolean.FALSE, Boolean.TRUE}, new String[]{"boolean", "boolean"});
                    RouteStatDump route = (RouteStatDump) unmarshaller.unmarshal(new StringReader(xml));

                    long count = route.getExchangesCompleted() + route.getExchangesFailed();
                    System.out.println(String.format(OUTPUT_FORMAT, route.getId(), count, route.getLastProcessingTime(), route.getDeltaProcessingTime(),
                            route.getMeanProcessingTime(), route.getMinProcessingTime(), route.getMaxProcessingTime(), route.getTotalProcessingTime(), route.getSelfProcessingTime()));

                    for (ProcessorStatDump ps : route.getProcessorStats()) {
                        // the self time is the total time of the processor itself
                        long selfTime = ps.getTotalProcessingTime();
                        count = ps.getExchangesCompleted() + ps.getExchangesFailed();
                        // indent route id with 2 spaces
                        System.out.println(String.format(OUTPUT_FORMAT, "  " + ps.getId(), count, ps.getLastProcessingTime(), ps.getDeltaProcessingTime(),
                                ps.getMeanProcessingTime(), ps.getMinProcessingTime(), ps.getMaxProcessingTime(), ps.getAccumulatedProcessingTime(), selfTime));
                    }
                }
            }
        } else {
            System.out.println("");
            System.out.println(stringEscape.unescapeJava("\u001B[31mJMX Agent of Camel is not reachable. Maybe it has been disabled on the Camel context"));
            System.out.println(stringEscape.unescapeJava("In consequence, profile are not available.\u001B[0m"));
        }

        // we want to group routes from the same context in the same table
        previousCamelContextName = camelContext.getName();
    }
}
