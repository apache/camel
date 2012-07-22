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
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.util.StringEscapeUtils;

/**
 * Command to display detailed information about a Camel context.
 */
@Command(scope = "camel", name = "context-info", description = "Display detailed information about a Camel context.")
public class ContextInfo extends OsgiCommandSupport {

    @Argument(index = 0, name = "name", description = "The name of the Camel context", required = true, multiValued = false)
    String name;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

    public Object doExecute() throws Exception {
        CamelContext camelContext = camelController.getCamelContext(name);

        if (camelContext == null) {
            System.err.println("Camel context " + name + " not found.");
            return null;
        }

        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1m\u001B[33mCamel Context " + name + "\u001B[0m"));
        System.out.println(StringEscapeUtils.unescapeJava("\tName: " + camelContext.getName()));
        System.out.println(StringEscapeUtils.unescapeJava("\tVersion: " + camelContext.getVersion()));
        System.out.println(StringEscapeUtils.unescapeJava("\tStatus: " + camelContext.getStatus()));
        System.out.println(StringEscapeUtils.unescapeJava("\tUptime: " + camelContext.getUptime()));

        // the statistics are in the mbenas
        ObjectName contextMBean = null;
        MBeanServer mBeanServer = camelContext.getManagementStrategy().getManagementAgent().getMBeanServer();
        Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(DefaultManagementAgent.DEFAULT_DOMAIN + ":type=context,name=\"" + name + "\",*"), null);
        Iterator<ObjectName> iterator = set.iterator();
        if (iterator.hasNext()) {
            contextMBean = iterator.next();
        }

        if (mBeanServer.isRegistered(contextMBean)) {
            Long exchangesTotal = (Long) mBeanServer.getAttribute(contextMBean, "ExchangesTotal");
            System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Total: " + exchangesTotal));
            Long exchangesCompleted = (Long) mBeanServer.getAttribute(contextMBean, "ExchangesCompleted");
            System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Completed: " + exchangesCompleted));
            Long exchangesFailed = (Long) mBeanServer.getAttribute(contextMBean, "ExchangesFailed");
            System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Failed: " + exchangesFailed));
            Long minProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "MinProcessingTime");
            System.out.println(StringEscapeUtils.unescapeJava("\tMin Processing Time: " + minProcessingTime + "ms"));
            Long maxProcessingTime = (Long) mBeanServer.getAttribute(contextMBean,  "MaxProcessingTime");
            System.out.println(StringEscapeUtils.unescapeJava("\tMax Processing Time: " + maxProcessingTime + "ms"));
            Long meanProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "MeanProcessingTime");
            System.out.println(StringEscapeUtils.unescapeJava("\tMean Processing Time: " + meanProcessingTime + "ms"));
            Long totalProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "TotalProcessingTime");
            System.out.println(StringEscapeUtils.unescapeJava("\tTotal Processing Time: " + totalProcessingTime + "ms"));
            Long lastProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "LastProcessingTime");
            System.out.println(StringEscapeUtils.unescapeJava("\tLast Processing Time: " + lastProcessingTime + "ms"));

            String load01 = (String) mBeanServer.getAttribute(contextMBean, "Load01");
            String load05 = (String) mBeanServer.getAttribute(contextMBean, "Load05");
            String load15 = (String) mBeanServer.getAttribute(contextMBean, "Load15");
            System.out.println(StringEscapeUtils.unescapeJava("\tLoad Avg: " + load01 + ", " + load05 + ", " + load15));

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            Object firstExchangeTimestampObj = mBeanServer.getAttribute(contextMBean, "FirstExchangeCompletedTimestamp");
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            if (firstExchangeTimestampObj == null) {
                // Print an empty value for scripting
                System.out.println(StringEscapeUtils.unescapeJava("\tFirst Exchange Date:"));
            } else {
                Date firstExchangeTimestamp = (Date) firstExchangeTimestampObj;
                System.out.println(StringEscapeUtils.unescapeJava("\tFirst Exchange Date: " + format.format(firstExchangeTimestamp)));
            }

            // Again, check for null to avoid NPE
            Object lastExchangeCompletedTimestampObj = mBeanServer.getAttribute(contextMBean, "LastExchangeCompletedTimestamp");
            if (lastExchangeCompletedTimestampObj == null) {
                // Print an empty value for scripting
                System.out.println(StringEscapeUtils.unescapeJava("\tLast Exchange Completed Date:"));
            } else {
                Date lastExchangeCompletedTimestamp = (Date) lastExchangeCompletedTimestampObj;
                System.out.println(StringEscapeUtils.unescapeJava("\tLast Exchange Completed Date: " + format.format(lastExchangeCompletedTimestamp)));
            }
        }

        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mAdvanced\u001B[0m"));
        System.out.println(StringEscapeUtils.unescapeJava("\tAuto Startup: " + camelContext.isAutoStartup()));
        System.out.println(StringEscapeUtils.unescapeJava("\tStarting Routes: " + camelContext.isStartingRoutes()));
        System.out.println(StringEscapeUtils.unescapeJava("\tSuspended: " + camelContext.isSuspended()));
        System.out.println(StringEscapeUtils.unescapeJava("\tTracing: " + camelContext.isTracing()));
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mProperties\u001B[0m"));
        for (String property : camelContext.getProperties().keySet()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + property + " = " + camelContext.getProperty(property)));
        }
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mComponents\u001B[0m"));
        for (String component : camelContext.getComponentNames()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + component));
        }
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mEndpoints\u001B[0m"));
        for (Endpoint endpoint : camelContext.getEndpoints()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + endpoint.getEndpointUri()));
        }
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mRoutes\u001B[0m"));
        for (Route route : camelContext.getRoutes()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + route.getId()));
        }
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mUsed Languages\u001B[0m"));
        for (String language : camelContext.getLanguageNames()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + language));
        }
        
        return null;
    }

}
