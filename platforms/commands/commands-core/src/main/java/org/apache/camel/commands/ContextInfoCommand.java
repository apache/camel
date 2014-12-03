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
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.spi.ManagementAgent;

import static org.apache.camel.util.UnitUtils.printUnitFromBytes;

/**
 * Command to display detailed information about a given {@link org.apache.camel.CamelContext}.
 */
public class ContextInfoCommand extends AbstractContextCommand {

    private StringEscape stringEscape;
    private String mode;

    /**
     * @param context The name of the Camel context
     * @param mode Allows for different display modes (--verbose, etc)
     */
    public ContextInfoCommand(String context, String mode) {
        super(context);
        this.mode = mode;
    }

    /**
     * Sets the {@link org.apache.camel.commands.StringEscape} to use.
     */
    public void setStringEscape(StringEscape stringEscape) {
        this.stringEscape = stringEscape;
    }

    @Override
    protected Object performContextCommand(CamelController camelController, CamelContext camelContext, PrintStream out, PrintStream err) throws Exception {

        out.println(stringEscape.unescapeJava("\u001B[1m\u001B[33mCamel Context " + context + "\u001B[0m"));
        out.println(stringEscape.unescapeJava("\tName: " + camelContext.getName()));
        out.println(stringEscape.unescapeJava("\tManagementName: " + camelContext.getManagementName()));
        out.println(stringEscape.unescapeJava("\tVersion: " + camelContext.getVersion()));
        out.println(stringEscape.unescapeJava("\tStatus: " + camelContext.getStatus()));
        out.println(stringEscape.unescapeJava("\tUptime: " + camelContext.getUptime()));

        // the statistics are in the mbeans
        printCamelManagedBeansStatus(camelContext, out);

        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mMiscellaneous\u001B[0m"));
        out.println(stringEscape.unescapeJava("\tAuto Startup: " + camelContext.isAutoStartup()));
        out.println(stringEscape.unescapeJava("\tStarting Routes: " + camelContext.isStartingRoutes()));
        out.println(stringEscape.unescapeJava("\tSuspended: " + camelContext.isSuspended()));
        out.println(stringEscape.unescapeJava("\tShutdown timeout: "
                + camelContext.getShutdownStrategy().getTimeUnit().toSeconds(camelContext.getShutdownStrategy().getTimeout()) + " sec."));
        out.println(stringEscape.unescapeJava("\tAllow UseOriginalMessage: " + camelContext.isAllowUseOriginalMessage()));
        out.println(stringEscape.unescapeJava("\tMessage History: " + camelContext.isMessageHistory()));
        out.println(stringEscape.unescapeJava("\tTracing: " + camelContext.isTracing()));
        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mProperties\u001B[0m"));
        for (Map.Entry<String, String> entry : camelContext.getProperties().entrySet()) {
            out.println(stringEscape.unescapeJava("\t" + entry.getKey() + " = " + entry.getValue()));
        }

        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mAdvanced\u001B[0m"));
        out.println(stringEscape.unescapeJava("\tClassResolver: " + camelContext.getClassResolver()));
        out.println(stringEscape.unescapeJava("\tPackageScanClassResolver: " + camelContext.getPackageScanClassResolver()));
        out.println(stringEscape.unescapeJava("\tApplicationContextClassLoader: " + camelContext.getApplicationContextClassLoader()));

        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mComponents\u001B[0m"));
        for (String component : camelContext.getComponentNames()) {
            System.out.println(stringEscape.unescapeJava("\t" + component));
        }

        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mDataformats\u001B[0m"));
        for (String name : camelContext.getDataFormats().keySet()) {
            out.println(stringEscape.unescapeJava("\t" + name));
        }

        if (mode != null && mode.equals("--verbose")) {
            out.println("");
            out.println(stringEscape.unescapeJava("\u001B[1mLanguages\u001B[0m"));
            for (String language : camelContext.getLanguageNames()) {
                out.println(stringEscape.unescapeJava("\t" + language));
            }

            out.println("");
            out.println(stringEscape.unescapeJava("\u001B[1mEndpoints\u001B[0m"));
            for (Endpoint endpoint : camelContext.getEndpoints()) {
                out.println(stringEscape.unescapeJava("\t" + endpoint.getEndpointUri()));
            }
        }

        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mRoutes\u001B[0m"));
        for (Route route : camelContext.getRoutes()) {
            out.println(stringEscape.unescapeJava("\t" + route.getId()));
        }

        return null;
    }

    protected void printCamelManagedBeansStatus(CamelContext camelContext, PrintStream out) throws Exception {
        // the statistics are in the mbeans
        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mStatistics\u001B[0m"));
        ObjectName contextMBean = null;
        ManagementAgent agent = camelContext.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();

            Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(agent.getMBeanObjectDomainName() + ":type=context,name=\"" + context + "\",*"), null);
            Iterator<ObjectName> iterator = set.iterator();
            if (iterator.hasNext()) {
                contextMBean = iterator.next();
            }

            if (mBeanServer.isRegistered(contextMBean)) {
                Long exchangesTotal = (Long) mBeanServer.getAttribute(contextMBean, "ExchangesTotal");
                out.println(stringEscape.unescapeJava("\tExchanges Total: " + exchangesTotal));
                Long exchangesCompleted = (Long) mBeanServer.getAttribute(contextMBean, "ExchangesCompleted");
                out.println(stringEscape.unescapeJava("\tExchanges Completed: " + exchangesCompleted));
                Long exchangesFailed = (Long) mBeanServer.getAttribute(contextMBean, "ExchangesFailed");
                out.println(stringEscape.unescapeJava("\tExchanges Failed: " + exchangesFailed));
                Long minProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "MinProcessingTime");
                out.println(stringEscape.unescapeJava("\tMin Processing Time: " + minProcessingTime + "ms"));
                Long maxProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "MaxProcessingTime");
                out.println(stringEscape.unescapeJava("\tMax Processing Time: " + maxProcessingTime + "ms"));
                Long meanProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "MeanProcessingTime");
                out.println(stringEscape.unescapeJava("\tMean Processing Time: " + meanProcessingTime + "ms"));
                Long totalProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "TotalProcessingTime");
                out.println(stringEscape.unescapeJava("\tTotal Processing Time: " + totalProcessingTime + "ms"));
                Long lastProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "LastProcessingTime");
                out.println(stringEscape.unescapeJava("\tLast Processing Time: " + lastProcessingTime + "ms"));
                Long deltaProcessingTime = (Long) mBeanServer.getAttribute(contextMBean, "DeltaProcessingTime");
                out.println(stringEscape.unescapeJava("\tDelta Processing Time: " + deltaProcessingTime + "ms"));

                String load01 = (String) mBeanServer.getAttribute(contextMBean, "Load01");
                String load05 = (String) mBeanServer.getAttribute(contextMBean, "Load05");
                String load15 = (String) mBeanServer.getAttribute(contextMBean, "Load15");
                out.println(stringEscape.unescapeJava("\tLoad Avg: " + load01 + ", " + load05 + ", " + load15));

                // Test for null to see if a any exchanges have been processed first to avoid NPE
                Object resetTimestampObj = mBeanServer.getAttribute(contextMBean, "ResetTimestamp");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                if (resetTimestampObj == null) {
                    // Print an empty value for scripting
                    out.println(stringEscape.unescapeJava("\tReset Statistics Date:"));
                } else {
                    Date firstExchangeTimestamp = (Date) resetTimestampObj;
                    out.println(stringEscape.unescapeJava("\tReset Statistics Date: " + format.format(firstExchangeTimestamp)));
                }

                // Test for null to see if a any exchanges have been processed first to avoid NPE
                Object firstExchangeTimestampObj = mBeanServer.getAttribute(contextMBean, "FirstExchangeCompletedTimestamp");
                if (firstExchangeTimestampObj == null) {
                    // Print an empty value for scripting
                    out.println(stringEscape.unescapeJava("\tFirst Exchange Date:"));
                } else {
                    Date firstExchangeTimestamp = (Date) firstExchangeTimestampObj;
                    out.println(stringEscape.unescapeJava("\tFirst Exchange Date: " + format.format(firstExchangeTimestamp)));
                }

                // Again, check for null to avoid NPE
                Object lastExchangeCompletedTimestampObj = mBeanServer.getAttribute(contextMBean, "LastExchangeCompletedTimestamp");
                if (lastExchangeCompletedTimestampObj == null) {
                    // Print an empty value for scripting
                    out.println(stringEscape.unescapeJava("\tLast Exchange Completed Date:"));
                } else {
                    Date lastExchangeCompletedTimestamp = (Date) lastExchangeCompletedTimestampObj;
                    out.println(stringEscape.unescapeJava("\tLast Exchange Completed Date: " + format.format(lastExchangeCompletedTimestamp)));
                }

                // add type converter statistics if enabled
                if (camelContext.getTypeConverterRegistry().getStatistics().isStatisticsEnabled()) {
                    out.println(stringEscape.unescapeJava(String.format("\tTypeConverterRegistry utilization: [attempts=%s, hits=%s, misses=%s, failures=%s]",
                            camelContext.getTypeConverterRegistry().getStatistics().getAttemptCounter(),
                            camelContext.getTypeConverterRegistry().getStatistics().getHitCounter(),
                            camelContext.getTypeConverterRegistry().getStatistics().getMissCounter(),
                            camelContext.getTypeConverterRegistry().getStatistics().getFailedCounter())));
                }

                // add stream caching details if enabled
                if (camelContext.getStreamCachingStrategy().isEnabled()) {
                    out.println(stringEscape.unescapeJava(
                            String.format("\tStreamCachingStrategy: [spoolDirectory=%s, spoolChiper=%s, spoolThreshold=%s, spoolUsedHeapMemoryThreshold=%s, "
                                            + "spoolUsedHeapMemoryLimit=%s, anySpoolRules=%s, bufferSize=%s, removeSpoolDirectoryWhenStopping=%s, statisticsEnabled=%s]",
                                    camelContext.getStreamCachingStrategy().getSpoolDirectory(),
                                    camelContext.getStreamCachingStrategy().getSpoolChiper(),
                                    camelContext.getStreamCachingStrategy().getSpoolThreshold(),
                                    camelContext.getStreamCachingStrategy().getSpoolUsedHeapMemoryThreshold(),
                                    camelContext.getStreamCachingStrategy().getSpoolUsedHeapMemoryLimit(),
                                    camelContext.getStreamCachingStrategy().isAnySpoolRules(),
                                    camelContext.getStreamCachingStrategy().getBufferSize(),
                                    camelContext.getStreamCachingStrategy().isRemoveSpoolDirectoryWhenStopping(),
                                    camelContext.getStreamCachingStrategy().getStatistics().isStatisticsEnabled())));

                    if (camelContext.getStreamCachingStrategy().getStatistics().isStatisticsEnabled()) {
                        out.println(stringEscape.unescapeJava(
                                String.format("\t                       [cacheMemoryCounter=%s, cacheMemorySize=%s, cacheMemoryAverageSize=%s, cacheSpoolCounter=%s, "
                                                + "cacheSpoolSize=%s, cacheSpoolAverageSize=%s]",
                                        camelContext.getStreamCachingStrategy().getStatistics().getCacheMemoryCounter(),
                                        printUnitFromBytes(camelContext.getStreamCachingStrategy().getStatistics().getCacheMemorySize()),
                                        printUnitFromBytes(camelContext.getStreamCachingStrategy().getStatistics().getCacheMemoryAverageSize()),
                                        camelContext.getStreamCachingStrategy().getStatistics().getCacheSpoolCounter(),
                                        printUnitFromBytes(camelContext.getStreamCachingStrategy().getStatistics().getCacheSpoolSize()),
                                        printUnitFromBytes(camelContext.getStreamCachingStrategy().getStatistics().getCacheSpoolAverageSize()))));
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

                out.println(stringEscape.unescapeJava("\tNumber of running routes: " + activeRoutes));
                out.println(stringEscape.unescapeJava("\tNumber of not running routes: " + inactiveRoutes));
            }

        } else {
            out.println("");
            out.println(stringEscape.unescapeJava("\u001B[31mJMX Agent of Camel is not reachable. Maybe it has been disabled on the Camel context"));
            out.println(stringEscape.unescapeJava("In consequence, some statistics are not available.\u001B[0m"));
        }

    }

}
