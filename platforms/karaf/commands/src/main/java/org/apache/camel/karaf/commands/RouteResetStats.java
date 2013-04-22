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

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.ManagementAgent;
import org.apache.felix.gogo.commands.Command;

/**
 * Command to reset route stats.
 */
@Command(scope = "camel", name = "route-reset-stats", description = "Reset performance stats on a route or group of routes")
public class RouteResetStats extends AbstractRouteCommand {

    @Override
    public void executeOnRoute(CamelContext camelContext, Route camelRoute) throws Exception {
        ManagementAgent agent = camelContext.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();

            // reset route mbeans
            ObjectName query = ObjectName.getInstance(agent.getMBeanObjectDomainName() + ":type=routes,*");
            Set<ObjectName> set = mBeanServer.queryNames(query, null);
            for (ObjectName routeMBean : set) {
                String camelId = (String) mBeanServer.getAttribute(routeMBean, "CamelId");
                if (camelId != null && camelId.equals(camelContext.getName())) {
                    mBeanServer.invoke(routeMBean, "reset", new Object[]{true}, new String[]{"boolean"});
                }
            }
        }
    }

}
