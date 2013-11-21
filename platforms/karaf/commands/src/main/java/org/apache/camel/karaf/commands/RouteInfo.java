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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ManagementAgent;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.util.StringEscapeUtils;

/**
 * Command to display detailed information about a Camel route.
 */
@Command(scope = "camel", name = "route-info", description = "Display information about a Camel route.")
public class RouteInfo extends CamelCommandSupport {

    @Argument(index = 0, name = "route", description = "The Camel route ID.", required = true, multiValued = false)
    String route;

    @Argument(index = 1, name = "context", description = "The Camel context name.", required = false, multiValued = false)
    String context;

    public Object doExecute() throws Exception {
        Route camelRoute = camelController.getRoute(route, context);

        if (camelRoute == null) {
            System.err.println("Camel route " + route + " not found.");
            return null;
        }

        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1m\u001B[33mCamel Route " + camelRoute.getId() + "\u001B[0m"));
        System.out.println(StringEscapeUtils.unescapeJava("\tCamel Context: " + camelRoute.getRouteContext().getCamelContext().getName()));
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mProperties\u001B[0m"));
        for (Map.Entry<String, Object> entry : camelRoute.getProperties().entrySet()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + entry.getKey() + " = " + entry.getValue()));
        }
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mStatistics\u001B[0m"));
        CamelContext camelContext = camelRoute.getRouteContext().getCamelContext();
        if (camelContext != null) {
            ManagementAgent agent = camelContext.getManagementStrategy().getManagementAgent();
            if (agent != null) {
                MBeanServer mBeanServer = agent.getMBeanServer();
                Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(agent.getMBeanObjectDomainName() + ":type=routes,name=\"" + route + "\",*"), null);
                Iterator<ObjectName> iterator = set.iterator();
                if (iterator.hasNext()) {
                    ObjectName routeMBean = iterator.next();

                    // the route must be part of the camel context
                    String camelId = (String) mBeanServer.getAttribute(routeMBean, "CamelId");
                    if (camelId != null && camelId.equals(camelContext.getName())) {
                        Integer inflightExchange = (Integer) mBeanServer.getAttribute(routeMBean, "InflightExchanges");
                        System.out.println(StringEscapeUtils.unescapeJava("\tInflight Exchanges: " + inflightExchange));
                        Long exchangesTotal = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesTotal");
                        System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Total: " + exchangesTotal));
                        Long exchangesCompleted = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesCompleted");
                        System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Completed: " + exchangesCompleted));
                        Long exchangesFailed = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesFailed");
                        System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Failed: " + exchangesFailed));
                        Long minProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "MinProcessingTime");
                        System.out.println(StringEscapeUtils.unescapeJava("\tMin Processing Time: " + minProcessingTime + " ms"));
                        Long maxProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "MaxProcessingTime");
                        System.out.println(StringEscapeUtils.unescapeJava("\tMax Processing Time: " + maxProcessingTime + " ms"));
                        Long meanProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "MeanProcessingTime");
                        System.out.println(StringEscapeUtils.unescapeJava("\tMean Processing Time: " + meanProcessingTime + " ms"));
                        Long totalProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "TotalProcessingTime");
                        System.out.println(StringEscapeUtils.unescapeJava("\tTotal Processing Time: " + totalProcessingTime + " ms"));
                        Long lastProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "LastProcessingTime");
                        System.out.println(StringEscapeUtils.unescapeJava("\tLast Processing Time: " + lastProcessingTime + " ms"));
                        Long deltaProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "DeltaProcessingTime");
                        System.out.println(StringEscapeUtils.unescapeJava("\tDelta Processing Time: " + deltaProcessingTime + " ms"));
                        String load01 = (String) mBeanServer.getAttribute(routeMBean, "Load01");
                        String load05 = (String) mBeanServer.getAttribute(routeMBean, "Load05");
                        String load15 = (String) mBeanServer.getAttribute(routeMBean, "Load15");
                        System.out.println(StringEscapeUtils.unescapeJava("\tLoad Avg: " + load01 + ", " + load05 + ", " + load15));

                        // Test for null to see if a any exchanges have been processed first to avoid NPE
                        Object resetTimestampObj = mBeanServer.getAttribute(routeMBean, "ResetTimestamp");
                        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        if (resetTimestampObj == null) {
                            // Print an empty value for scripting
                            System.out.println(StringEscapeUtils.unescapeJava("\tReset Statistics Date:"));
                        } else {
                            Date firstExchangeTimestamp = (Date) resetTimestampObj;
                            System.out.println(StringEscapeUtils.unescapeJava("\tReset Statistics Date: " + format.format(firstExchangeTimestamp)));
                        }

                        // Test for null to see if a any exchanges have been processed first to avoid NPE
                        Object firstExchangeTimestampObj = mBeanServer.getAttribute(routeMBean, "FirstExchangeCompletedTimestamp");
                        if (firstExchangeTimestampObj == null) {
                            // Print an empty value for scripting
                            System.out.println(StringEscapeUtils.unescapeJava("\tFirst Exchange Date:"));
                        } else {
                            Date firstExchangeTimestamp = (Date) firstExchangeTimestampObj;
                            System.out.println(StringEscapeUtils.unescapeJava("\tFirst Exchange Date: " + format.format(firstExchangeTimestamp)));
                        }

                        // Again, check for null to avoid NPE
                        Object lastExchangeCompletedTimestampObj = mBeanServer.getAttribute(routeMBean, "LastExchangeCompletedTimestamp");
                        if (lastExchangeCompletedTimestampObj == null) {
                            // Print an empty value for scripting
                            System.out.println(StringEscapeUtils.unescapeJava("\tLast Exchange Completed Date:"));
                        } else {
                            Date lastExchangeCompletedTimestamp = (Date) lastExchangeCompletedTimestampObj;
                            System.out.println(StringEscapeUtils.unescapeJava("\tLast Exchange Completed Date: " + format.format(lastExchangeCompletedTimestamp)));
                        }
                    }
                }
            } else {
                System.out.println("");
                System.out.println(StringEscapeUtils.unescapeJava("\u001B[31mJMX Agent of Camel is not reachable. Maybe it has been disabled on the Camel context"));
                System.out.println(StringEscapeUtils.unescapeJava("In consequence, some statistics are not available.\u001B[0m"));
            }

            System.out.println("");
            System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mDefinition\u001B[0m"));
            RouteDefinition definition = camelController.getRouteDefinition(route, camelRoute.getRouteContext().getCamelContext().getName());
            System.out.println(StringEscapeUtils.unescapeJava(ModelHelper.dumpModelAsXml(definition)));
        }
        return null;
    }
}
