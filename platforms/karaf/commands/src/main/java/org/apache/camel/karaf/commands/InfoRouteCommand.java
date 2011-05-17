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
import org.apache.camel.Route;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.ModelHelper;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.util.StringEscapeUtils;

/**
 * Command to display detailed information about a Camel route.
 */
@Command(scope = "camel", name = "info-route", description = "Display information about a Camel route.")
public class InfoRouteCommand extends OsgiCommandSupport {

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

        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1m\u001B[33mCamel Route " + camelRoute.getId() + "\u001B[0m"));
        System.out.println(StringEscapeUtils.unescapeJava("\tCamel Context: " + camelRoute.getRouteContext().getCamelContext().getName()));
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mProperties\u001B[0m"));
        for (String property : camelRoute.getProperties().keySet()) {
            System.out.println(StringEscapeUtils.unescapeJava("\t\t" + property + " = " + camelRoute.getProperties().get(property)));
        }
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mStatistics\u001B[0m"));
        CamelContext camelContext = camelRoute.getRouteContext().getCamelContext();
        if (camelContext != null) {
            MBeanServer mBeanServer = camelContext.getManagementStrategy().getManagementAgent().getMBeanServer();
            Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(DefaultManagementAgent.DEFAULT_DOMAIN + ":type=routes,name=\"" + route + "\",*"), null);
            Iterator<ObjectName> iterator = set.iterator();
            if (iterator.hasNext()) {
                ObjectName routeMBean = iterator.next();
                Long exchangesTotal = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesTotal");
                System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Total: " + exchangesTotal));
                Long exchangesCompleted = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesCompleted");
                System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Completed: " + exchangesCompleted));
                Long exchangesFailed = (Long) mBeanServer.getAttribute(routeMBean, "ExchangesFailed");
                System.out.println(StringEscapeUtils.unescapeJava("\tExchanges Failed: " + exchangesFailed));
                Long minProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "MinProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tMin Processing Time: " + minProcessingTime + "ms"));
                Long maxProcessingTime = (Long) mBeanServer.getAttribute(routeMBean,  "MaxProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tMax Processing Time: " + maxProcessingTime + "ms"));
                Long meanProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "MeanProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tMean Processing Time: " + meanProcessingTime + "ms"));
                Long totalProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "TotalProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tTotal Processing Time: " + totalProcessingTime + "ms"));
                Long lastProcessingTime = (Long) mBeanServer.getAttribute(routeMBean, "LastProcessingTime");
                System.out.println(StringEscapeUtils.unescapeJava("\tLast Processing Time: " + lastProcessingTime + "ms"));
                Date firstExchangeTimestamp = (Date) mBeanServer.getAttribute(routeMBean, "FirstExchangeCompletedTimestamp");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                System.out.println(StringEscapeUtils.unescapeJava("\tFirst Exchange Date: " + format.format(firstExchangeTimestamp)));
                Date lastExchangeCompletedTimestamp = (Date) mBeanServer.getAttribute(routeMBean, "LastExchangeCompletedTimestamp");
                System.out.println(StringEscapeUtils.unescapeJava("\tLast Exchange Completed Date: " + format.format(lastExchangeCompletedTimestamp)));
            }
        }
        System.out.println("");
        System.out.println(StringEscapeUtils.unescapeJava("\u001B[1mDefinition\u001B[0m"));
        RouteDefinition definition = camelController.getRouteDefinition(route, camelRoute.getRouteContext().getCamelContext().getName());
        System.out.println(StringEscapeUtils.unescapeJava(ModelHelper.dumpModelAsXml(definition)));
        return null;
    }

}
