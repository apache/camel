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
package org.apache.camel.web.util;

import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.management.ManagedManagementStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gathers {@link RouteStatistics} from JMX.
 * <p/>
 * If JMX is disabled then no statistics can be gathered, and <tt>null</tt> is returned.
 */
public class JMXRouteStatistics implements RouteStatistics {

    private static final Logger LOG = LoggerFactory.getLogger(JMXRouteStatistics.class);

    public Object getRouteStatistic(CamelContext camelContext, String routeID, String attribute) {
        // only possible if JMX is enabled
        if (!(camelContext.getManagementStrategy() instanceof ManagedManagementStrategy)) {
            return null;
        }

        try {
            MBeanServer server = camelContext.getManagementStrategy().getManagementAgent().getMBeanServer();
            String domain = camelContext.getManagementStrategy().getManagementAgent().getMBeanObjectDomainName();

            ObjectName objName = new ObjectName(domain + ":type=routes,*");
            List<ObjectName> cacheList = new LinkedList<ObjectName>(server.queryNames(objName, null));
            for (Iterator<ObjectName> iter = cacheList.iterator(); iter.hasNext();) {
                objName = iter.next();
                String keyProps = objName.getCanonicalKeyPropertyListString();
                ObjectName objectInfoName = new ObjectName(domain + ":" + keyProps);
                String currentRouteID = (String) server.getAttribute(objectInfoName, "RouteId");
                if (currentRouteID.equals(routeID)) {
                    Object value = server.getAttribute(objectInfoName, attribute);
                    if (value instanceof Date) {
                        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                        return df.format(value);
                    } else {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error getting route statistic from JMX. This exception will be ignored.", e);
        }

        return null;
    }

}
