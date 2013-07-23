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
import java.util.List;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.spi.ManagementAgent;
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

    @Argument(index = 1, name = "mode", description = "Allows for different display modes (--verbose, etc)", required = false, multiValued = false)
    String mode;

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
        System.out.println(StringEscapeUtils.unescapeJava("\tManagementName: " + camelContext.getManagementName()));
        System.out.println(StringEscapeUtils.unescapeJava("\tVersion: " + camelContext.getVersion()));
        System.out.println(StringEscapeUtils.unescapeJava("\tStatus: " + camelContext.getStatus()));
        System.out.println(StringEscapeUtils.unescapeJava("\tUptime: " + camelContext.getUptime()));

        // the statistics are in the mbeans
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mStatistics\u001B[0m"));
        ObjectName contextMBean = null;
        ManagementAgent agent = camelContext.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();

            Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(agent.getMBeanObjectDomainName() + ":type=context,name=\"" + name + "\",*"), null);
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
                Long maxProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "MaxProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tMax Processing Time: " + maxProcessingTime + "ms"));
                Long meanProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "MeanProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tMean Processing Time: " + meanProcessingTime + "ms"));
                Long totalProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "TotalProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tTotal Processing Time: " + totalProcessingTime + "ms"));
                Long lastProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "LastProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tLast Processing Time: " + lastProcessingTime + "ms"));
                Long deltaProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "DeltaProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tDelta Processing Time: " + deltaProcessingTime + "ms"));

                String load01 = (String) mBeanServer.getAttribute(contextMBean, "Load01");
                String load05 = (String) mBeanServer.getAttribute(contextMBean, "Load05");
                String load15 = (String) mBeanServer.getAttribute(contextMBean, "Load15");
                System.out.println(StringEscapeUtils.unescapeJava("\tLoad Avg: " + load01 + ", " + load05 + ", " + load15));

                // Test for null to see if a any exchanges have been processed first to avoid NPE
                Object resetTimestampObj = mBeanServer.getAttribute(contextMBean, "ResetTimestamp");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                if (resetTimestampObj == null) {
                    // Print an empty value for scripting
                    System.out.println(StringEscapeUtils.unescapeJava("\tReset Statistics Date:"));
                } else {
                    Date firstExchangeTimestamp = (Date) resetTimestampObj;
                    System.out.println(StringEscapeUtils.unescapeJava("\tReset Statistics Date: " + format.format(firstExchangeTimestamp)));
                }

                // Test for null to see if a any exchanges have been processed first to avoid NPE
                Object firstExchangeTimestampObj = mBeanServer.getAttribute(contextMBean, "FirstExchangeCompletedTimestamp");
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

                // add type converter statistics if enabled
                if (camelContext.getTypeConverterRegistry().getStatistics().isStatisticsEnabled()) {
                    System.out.println(StringEscapeUtils.unescapeJava(String.format("\tTypeConverterRegistry utilization: [attempts=%s, hits=%s, misses=%s, failures=%s]",
                            camelContext.getTypeConverterRegistry().getStatistics().getAttemptCounter(),
                            camelContext.getTypeConverterRegistry().getStatistics().getHitCounter(),
                            camelContext.getTypeConverterRegistry().getStatistics().getMissCounter(),
                            camelContext.getTypeConverterRegistry().getStatistics().getFailedCounter())));
                }

                // add stream caching details if enabled
                if (camelContext.getStreamCachingStrategy().isEnabled()) {
                    System.out.println(StringEscapeUtils.unescapeJava(String.format("\tStreamCachingStrategy: [spoolDirectory=%s, spoolChiper=%s, spoolThreshold=%s, spoolUsedHeapMemoryThreshold=%s, anySpoolRules=%s, bufferSize=%s, removeSpoolDirectoryWhenStopping=%s, statisticsEnabled=%s]",
                            camelContext.getStreamCachingStrategy().getSpoolDirectory(),
                            camelContext.getStreamCachingStrategy().getSpoolChiper(),
                            camelContext.getStreamCachingStrategy().getSpoolThreshold(),
                            camelContext.getStreamCachingStrategy().getSpoolUsedHeapMemoryThreshold(),
                            camelContext.getStreamCachingStrategy().isAnySpoolRules(),
                            camelContext.getStreamCachingStrategy().getBufferSize(),
                            camelContext.getStreamCachingStrategy().isRemoveSpoolDirectoryWhenStopping(),
                            camelContext.getStreamCachingStrategy().getStatistics().isStatisticsEnabled())));

                    if (camelContext.getStreamCachingStrategy().getStatistics().isStatisticsEnabled()) {
                        System.out.println(StringEscapeUtils.unescapeJava(String.format("\t                       [cacheMemoryCounter=%s, cacheMemorySize=%s, cacheMemoryAverageSize=%s, cacheSpoolCounter=%s, cacheSpoolSize=%s, cacheSpoolAverageSize=%s]",
                                camelContext.getStreamCachingStrategy().getStatistics().getCacheMemoryCounter(),
                                camelContext.getStreamCachingStrategy().getStatistics().getCacheMemorySize(),
                                camelContext.getStreamCachingStrategy().getStatistics().getCacheMemoryAverageSize(),
                                camelContext.getStreamCachingStrategy().getStatistics().getCacheSpoolCounter(),
                                camelContext.getStreamCachingStrategy().getStatistics().getCacheSpoolSize(),
                                camelContext.getStreamCachingStrategy().getStatistics().getCacheSpoolAverageSize())));
                    }
                }

                long activeRoutes = 0;
                long inactiveRoutes = 0;
                List<Route> routeList = camelContext.getRoutes();
                for (Route route : routeList) {
                    if (camelContext.getRouteStatus(route.getId()).isStarted()) {
                        activeRoutes++;
                    } else {
                        inactiveRoutes++;
                    }
                }

                System.out.println(StringEscapeUtils.unescapeJava("\tNumber of running routes: " + activeRoutes));
                System.out.println(StringEscapeUtils.unescapeJava("\tNumber of not running routes: " + inactiveRoutes));
            }

        } else {
            System.out.println("");
            System.out.println(StringEscapeUtils.unescapeJava("\u001B[31mJMX Agent of Camel is not reachable. Maybe it has been disabled on the Camel context"));
            System.out.println(StringEscapeUtils.unescapeJava("In consequence, some statistics are not available.\u001B[0m"));
        }

        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mMiscellaneous\u001B[0m"));
        System.out.println(StringEscapeUtils.unescapeJava("\tAuto Startup: " + camelContext.isAutoStartup()));
        System.out.println(StringEscapeUtils.unescapeJava("\tStarting Routes: " + camelContext.isStartingRoutes()));
        System.out.println(StringEscapeUtils.unescapeJava("\tSuspended: " + camelContext.isSuspended()));
        System.out.println(StringEscapeUtils.unescapeJava("\tMessage History: " + camelContext.isMessageHistory()));
        System.out.println(StringEscapeUtils.unescapeJava("\tTracing: " + camelContext.isTracing()));
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mProperties\u001B[0m"));
        for (String property : camelContext.getProperties().keySet()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + property + " = " + camelContext.getProperty(property)));
        }

        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mAdvanced\u001B[0m"));
        System.out.println(StringEscapeUtils.unescapeJava("\tClassResolver: " + camelContext.getClassResolver()));
        System.out.println(StringEscapeUtils.unescapeJava("\tPackageScanClassResolver: " + camelContext.getPackageScanClassResolver()));
        System.out.println(StringEscapeUtils.unescapeJava("\tApplicationContextClassLoader: " + camelContext.getApplicationContextClassLoader()));

        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mComponents\u001B[0m"));
        for (String component : camelContext.getComponentNames()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + component));
        }

        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mDataformats\u001B[0m"));
        for (String names : camelContext.getDataFormats().keySet()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + names));
        }

        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mLanguages\u001B[0m"));
        for (String language : camelContext.getLanguageNames()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + language));
        }

        if (mode != null && mode.equals("--verbose")) {
            System.out.println("");
            System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mEndpoints\u001B[0m"));
            for (Endpoint endpoint : camelContext.getEndpoints()) {
                System.out.println(StringEscapeUtils.unescapeJava("\t" + endpoint.getEndpointUri()));
            }
        }

        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mRoutes\u001B[0m"));
        for (Route route : camelContext.getRoutes()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t" + route.getId()));
        }

        return null;
    }

}
