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
import java.util.Iterator;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.util.ProcessorStatDump;
import org.apache.camel.util.RouteStatDump;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.util.StringEscapeUtils;

/**
 * Command to display profile information about a Camel route.
 */
@Command(scope = "camel", name = "route-profile", description = "Display profile information about a Camel route.")
public class RouteProfile extends OsgiCommandSupport {

    protected static final String HEADER_FORMAT = "%-30s %10s %10s %12s %12s %12s %12s %12s";
    protected static final String OUTPUT_FORMAT = "[%-28s] [%8d] [%8d] [%10d] [%10d] [%10d] [%10d] [%10d]";

    @Argument(index = 0, name = "route", description = "The Camel route ID.", required = true, multiValued = false)
    String route;

    @Argument(index = 1, name = "context", description = "The Camel context name.", required = false, multiValued = false)
    String context;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

    public Object doExecute() throws Exception {
        Route camelRoute = camelController.getRoute(route, context);

        if (camelRoute == null) {
            System.err.println("Camel route " + route + " not found.");
            return null;
        }

        JAXBContext context = JAXBContext.newInstance(RouteStatDump.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1m\u001B[33mCamel Route " + camelRoute.getId() + "\u001B[0m"));
        System.out.println(StringEscapeUtils.unescapeJava("\tCamel Context: " + camelRoute.getRouteContext().getCamelContext().getName()));
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mProfile\u001B[0m"));
        CamelContext camelContext = camelRoute.getRouteContext().getCamelContext();
        if (camelContext != null) {
            ManagementAgent agent = camelContext.getManagementStrategy().getManagementAgent();
            if (agent != null) {
                MBeanServer mBeanServer = agent.getMBeanServer();
                Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(DefaultManagementAgent.DEFAULT_DOMAIN + ":type=routes,name=\"" + route + "\",*"), null);
                Iterator<ObjectName> iterator = set.iterator();
                if (iterator.hasNext()) {
                    ObjectName routeMBean = iterator.next();

                    // TODO: add a row with the route endpoint, so you can see that
                    // TODO: add column with total time (delta for self time)
                    // TODO: ensure the jmx mbeans for processors is sorted correctly

                    String xml = (String) mBeanServer.invoke(routeMBean, "dumpRouteStatsAsXml", new Object[]{Boolean.FALSE, Boolean.TRUE}, new String[]{"boolean", "boolean"});
                    RouteStatDump route = (RouteStatDump) unmarshaller.unmarshal(new StringReader(xml));

                    System.out.println(String.format(HEADER_FORMAT, "Id", "Completed", "Failed", "Last (ms)", "Mean (ms)", "Min (ms)", "Max (ms)", "Self (ms)"));
                    System.out.println(String.format(OUTPUT_FORMAT, route.getId(), route.getExchangesCompleted(), route.getExchangesFailed(), route.getLastProcessingTime(),
                            route.getMeanProcessingTime(), route.getMinProcessingTime(), route.getMaxProcessingTime(), route.getTotalProcessingTime(), 0));

                    // output in reverse order which prints the route as we want
                    for (int i = route.getProcessorStats().size() - 1; i >= 0; i--) {
                        ProcessorStatDump ps = route.getProcessorStats().get(i);
                        // the self time is the total time of the processor itself
                        long selfTime = ps.getTotalProcessingTime();
                        // indent route id with 2 spaces
                        System.out.println(String.format(OUTPUT_FORMAT, "  " + ps.getId(), ps.getExchangesCompleted(), ps.getExchangesFailed(), ps.getLastProcessingTime(),
                                ps.getMeanProcessingTime(), ps.getMinProcessingTime(), ps.getMaxProcessingTime(), selfTime));
                    }
                }
            } else {
                System.out.println("");
                System.out.println(StringEscapeUtils.unescapeJava("\u001B[31mJMX Agent of Camel is not reachable. Maybe it has been disabled on the Camel context"));
                System.out.println(StringEscapeUtils.unescapeJava("In consequence, profile are not available.\u001B[0m"));
            }
        }
        return null;
    }
}
