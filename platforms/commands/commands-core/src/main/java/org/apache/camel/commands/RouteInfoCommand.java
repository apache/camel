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

public class RouteInfoCommand extends AbstractRouteCommand {

    private StringEscape stringEscape;

    public RouteInfoCommand(String route, String context) {
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
        out.println(stringEscape.unescapeJava("\u001B[1m\u001B[33mCamel Route " + camelRoute.getId() + "\u001B[0m"));
        out.println(stringEscape.unescapeJava("\tCamel Context: " + camelRoute.getRouteContext().getCamelContext().getName()));
        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mProperties\u001B[0m"));
        for (Map.Entry<String, Object> entry : camelRoute.getProperties().entrySet()) {
            out.println(stringEscape.unescapeJava("\t" + entry.getKey() + " = " + entry.getValue()));
        }
        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mStatistics\u001B[0m"));
        if (camelContext != null) {
            ManagementAgent agent = camelContext.getManagementStrategy().getManagementAgent();
            if (agent != null) {
                MBeanServer mBeanServer = agent.getMBeanServer();
                Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(agent.getMBeanObjectDomainName() + ":type=routes,name=\"" + camelRoute.getId() + "\",*"), null);
                Iterator<ObjectName> iterator = set.iterator();
                if (iterator.hasNext()) {
                    ObjectName routeMBean = iterator.next();

                    // the route must be part of the camel context
                    String camelId = (String) mBeanServer.getAttribute(routeMBean, "CamelId");
                    if (camelId != null && camelId.equals(camelContext.getName())) {
                        Integer inflightExchange = (Integer) mBeanServer.getAttribute(routeMBean, "InflightExchanges");
                        out.println(stringEscape.unescapeJava("\tInflight Exchanges: " + inflightExchange));
                        Long exchangesTotal = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesTotal");
                        out.println(stringEscape.unescapeJava("\tExchanges Total: " + exchangesTotal));
                        Long exchangesCompleted = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesCompleted");
                        out.println(stringEscape.unescapeJava("\tExchanges Completed: " + exchangesCompleted));
                        Long exchangesFailed = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesFailed");
                        out.println(stringEscape.unescapeJava("\tExchanges Failed: " + exchangesFailed));
                        Long minProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "MinProcessingTime");
                        out.println(stringEscape.unescapeJava("\tMin Processing Time: " + minProcessingTime + " ms"));
                        Long maxProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "MaxProcessingTime");
                        out.println(stringEscape.unescapeJava("\tMax Processing Time: " + maxProcessingTime + " ms"));
                        Long meanProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "MeanProcessingTime");
                        out.println(stringEscape.unescapeJava("\tMean Processing Time: " + meanProcessingTime + " ms"));
                        Long totalProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "TotalProcessingTime");
                        out.println(stringEscape.unescapeJava("\tTotal Processing Time: " + totalProcessingTime + " ms"));
                        Long lastProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "LastProcessingTime");
                        out.println(stringEscape.unescapeJava("\tLast Processing Time: " + lastProcessingTime + " ms"));
                        Long deltaProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "DeltaProcessingTime");
                        out.println(stringEscape.unescapeJava("\tDelta Processing Time: " + deltaProcessingTime + " ms"));
                        String load01 = (String) mBeanServer.getAttribute(routeMBean, "Load01");
                        String load05 = (String) mBeanServer.getAttribute(routeMBean, "Load05");
                        String load15 = (String) mBeanServer.getAttribute(routeMBean, "Load15");
                        out.println(stringEscape.unescapeJava("\tLoad Avg: " + load01 + ", " + load05 + ", " + load15));

                        // Test for null to see if a any exchanges have been processed first to avoid NPE
                        Object resetTimestampObj = mBeanServer.getAttribute(routeMBean, "ResetTimestamp");
                        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        if (resetTimestampObj == null) {
                            // Print an empty value for scripting
                            out.println(stringEscape.unescapeJava("\tReset Statistics Date:"));
                        } else {
                            Date firstExchangeTimestamp = (Date) resetTimestampObj;
                            out.println(stringEscape.unescapeJava("\tReset Statistics Date: " + format.format(firstExchangeTimestamp)));
                        }

                        // Test for null to see if a any exchanges have been processed first to avoid NPE
                        Object firstExchangeTimestampObj = mBeanServer.getAttribute(routeMBean, "FirstExchangeCompletedTimestamp");
                        if (firstExchangeTimestampObj == null) {
                            // Print an empty value for scripting
                            out.println(stringEscape.unescapeJava("\tFirst Exchange Date:"));
                        } else {
                            Date firstExchangeTimestamp = (Date) firstExchangeTimestampObj;
                            out.println(stringEscape.unescapeJava("\tFirst Exchange Date: " + format.format(firstExchangeTimestamp)));
                        }

                        // Again, check for null to avoid NPE
                        Object lastExchangeCompletedTimestampObj = mBeanServer.getAttribute(routeMBean, "LastExchangeCompletedTimestamp");
                        if (lastExchangeCompletedTimestampObj == null) {
                            // Print an empty value for scripting
                            out.println(stringEscape.unescapeJava("\tLast Exchange Completed Date:"));
                        } else {
                            Date lastExchangeCompletedTimestamp = (Date) lastExchangeCompletedTimestampObj;
                            out.println(stringEscape.unescapeJava("\tLast Exchange Completed Date: " + format.format(lastExchangeCompletedTimestamp)));
                        }
                    }
                }
            } else {
                out.println("");
                out.println(stringEscape.unescapeJava("\u001B[31mJMX Agent of Camel is not reachable. Maybe it has been disabled on the Camel context"));
                out.println(stringEscape.unescapeJava("In consequence, some statistics are not available.\u001B[0m"));
            }

            out.println("");
            out.println(stringEscape.unescapeJava("\u001B[1mDefinition\u001B[0m"));
            RouteDefinition definition = camelController.getRouteDefinition(camelRoute.getId(), camelRoute.getRouteContext().getCamelContext().getName());
            out.println(stringEscape.unescapeJava(ModelHelper.dumpModelAsXml(definition)));
        }
    }
}
