/*
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
package org.apache.camel.test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <code>CamelRouteCoverageDumper</code> instance dumps the route coverage of
 * a given camel test.
 */
public class CamelRouteCoverageDumper {

    private static final Logger LOG = LoggerFactory.getLogger(CamelRouteCoverageDumper.class);

    public void dump(ManagedCamelContextMBean managedCamelContext, ModelCamelContext context, String dumpDir, String dumpFilename, String testClass, String testName,
                     long testTimeTaken)
        throws Exception {
        logCoverageSummary(managedCamelContext, context);

        String routeCoverageAsXml = managedCamelContext.dumpRoutesCoverageAsXml();
        String combined = "<camelRouteCoverage>\n" + gatherTestDetailsAsXml(testClass, testName, testTimeTaken) + routeCoverageAsXml + "\n</camelRouteCoverage>";

        File dumpFile = new File(dumpDir);
        // ensure dir exists
        dumpFile.mkdirs();
        dumpFile = new File(dumpDir, dumpFilename);

        LOG.info("Dumping route coverage to file: {}", dumpFile);
        InputStream is = new ByteArrayInputStream(combined.getBytes());
        OutputStream os = new FileOutputStream(dumpFile, false);
        IOHelper.copyAndCloseInput(is, os);
        IOHelper.close(os);
    }

    /**
     * Groups all processors from Camel context by route id.
     */
    private Map<String, List<ManagedProcessorMBean>> findProcessorsForEachRoute(MBeanServer server, ModelCamelContext context)
        throws MalformedObjectNameException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        String domain = context.getManagementStrategy().getManagementAgent().getMBeanServerDefaultDomain();

        Map<String, List<ManagedProcessorMBean>> processorsForRoute = new HashMap<>();

        ObjectName processorsObjectName = new ObjectName(domain + ":context=" + context.getManagementName() + ",type=processors,name=*");
        Set<ObjectName> objectNames = server.queryNames(processorsObjectName, null);

        for (ObjectName objectName : objectNames) {
            String routeId = server.getAttribute(objectName, "RouteId").toString();
            String name = objectName.getKeyProperty("name");
            name = ObjectName.unquote(name);

            ManagedProcessorMBean managedProcessor = context.getExtension(ManagedCamelContext.class).getManagedProcessor(name);

            if (managedProcessor != null) {
                if (processorsForRoute.get(routeId) == null) {
                    List<ManagedProcessorMBean> processorsList = new ArrayList<>();
                    processorsList.add(managedProcessor);

                    processorsForRoute.put(routeId, processorsList);
                } else {
                    processorsForRoute.get(routeId).add(managedProcessor);
                }
            }
        }

        // sort processors by position in route definition
        for (Map.Entry<String, List<ManagedProcessorMBean>> entry : processorsForRoute.entrySet()) {
            Collections.sort(entry.getValue(), Comparator.comparing(ManagedProcessorMBean::getIndex));
        }

        return processorsForRoute;
    }

    /**
     * Gathers test details as xml.
     */
    private String gatherTestDetailsAsXml(String testClass, String testName, long timeTaken) {
        StringBuilder sb = new StringBuilder();
        sb.append("<test>\n");
        sb.append("  <class>").append(testClass).append("</class>\n");
        sb.append("  <method>").append(testName).append("</method>\n");
        sb.append("  <time>").append(timeTaken).append("</time>\n");
        sb.append("</test>\n");
        return sb.toString();
    }

    /**
     * Logs route coverage summary, including which routes are uncovered and
     * what is the coverage of each processor in each route.
     */
    private void logCoverageSummary(ManagedCamelContextMBean managedCamelContext, ModelCamelContext context) throws Exception {
        StringBuilder builder = new StringBuilder("\nCoverage summary\n");

        int routes = managedCamelContext.getTotalRoutes();

        long contextExchangesTotal = managedCamelContext.getExchangesTotal();

        List<String> uncoveredRoutes = new ArrayList<>();

        StringBuilder routesSummary = new StringBuilder();
        routesSummary.append("\tProcessor coverage\n");

        MBeanServer server = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        Map<String, List<ManagedProcessorMBean>> processorsForRoute = findProcessorsForEachRoute(server, context);

        // log processor coverage for each route
        for (Route route : context.getRoutes()) {
            ManagedRouteMBean managedRoute = context.getExtension(ManagedCamelContext.class).getManagedRoute(route.getId());
            if (managedRoute.getExchangesTotal() == 0) {
                uncoveredRoutes.add(route.getId());
            }

            long routeCoveragePercentage = Math.round((double)managedRoute.getExchangesTotal() / contextExchangesTotal * 100);
            routesSummary.append("\t\tRoute ").append(route.getId()).append(" total: ").append(managedRoute.getExchangesTotal()).append(" (").append(routeCoveragePercentage)
                .append("%)\n");

            if (server != null) {
                List<ManagedProcessorMBean> processors = processorsForRoute.get(route.getId());
                if (processors != null) {
                    for (ManagedProcessorMBean managedProcessor : processors) {
                        String processorId = managedProcessor.getProcessorId();
                        long processorExchangesTotal = managedProcessor.getExchangesTotal();
                        long processorCoveragePercentage = Math.round((double)processorExchangesTotal / contextExchangesTotal * 100);
                        routesSummary.append("\t\t\tProcessor ").append(processorId).append(" total: ").append(processorExchangesTotal).append(" (")
                            .append(processorCoveragePercentage).append("%)\n");
                    }
                }
            }
        }

        int used = routes - uncoveredRoutes.size();

        long contextPercentage = Math.round((double)used / routes * 100);
        builder.append("\tRoute coverage: ").append(used).append(" out of ").append(routes).append(" routes used (").append(contextPercentage).append("%)\n");
        builder.append("\t\tCamelContext (").append(managedCamelContext.getCamelId()).append(") total: ").append(contextExchangesTotal).append("\n");

        if (uncoveredRoutes.size() > 0) {
            builder.append("\t\tUncovered routes: ").append(uncoveredRoutes.stream().collect(Collectors.joining(", "))).append("\n");
        }

        builder.append(routesSummary);
        LOG.info(builder.toString());
    }

}
