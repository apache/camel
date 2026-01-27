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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.mbean.ManagedProducerMBean;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "producer", displayName = "Producers", description = "Display information about Camel producers")
public class ProducerDevConsole extends AbstractDevConsole {

    public ProducerDevConsole() {
        super("camel", "producer", "Producers", "Display information about Camel producers");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        Set<ObjectName> producers = queryProducerMBeans();
        if (producers == null) {
            return sb.toString();
        }

        for (ObjectName on : producers) {
            ManagedProducerMBean mp = getManagedProducer(getCamelContext(), on);
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            appendProducerText(sb, mp);
        }

        return sb.toString();
    }

    private void appendProducerText(StringBuilder sb, ManagedProducerMBean mp) {
        sb.append(String.format("%n    Uri: %s", mp.getEndpointUri()));
        sb.append(String.format("%n    State: %s", mp.getState()));
        sb.append(String.format("%n    Class: %s", mp.getServiceType()));
        sb.append(String.format("%n    Remote: %b", mp.isRemoteEndpoint()));
        sb.append(String.format("%n    Singleton: %b", mp.isSingleton()));
        if (mp.getRouteId() != null) {
            sb.append(String.format("%n    Route Id: %s", mp.getRouteId()));
        }
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        final JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();
        root.put("producers", list);

        Set<ObjectName> producers = queryProducerMBeans();
        if (producers == null) {
            return root;
        }

        for (ObjectName on : producers) {
            ManagedProducerMBean mp = getManagedProducer(getCamelContext(), on);
            list.add(buildProducerJson(mp));
        }

        return root;
    }

    private JsonObject buildProducerJson(ManagedProducerMBean mp) {
        JsonObject jo = new JsonObject();
        jo.put("uri", mp.getEndpointUri());
        jo.put("state", mp.getState());
        jo.put("class", mp.getServiceType());
        jo.put("remote", mp.isRemoteEndpoint());
        jo.put("singleton", mp.isSingleton());
        if (mp.getRouteId() != null) {
            jo.put("routeId", mp.getRouteId());
        }
        return jo;
    }

    private Set<ObjectName> queryProducerMBeans() {
        MBeanServer mbeanServer = getCamelContext().getManagementStrategy().getManagementAgent().getMBeanServer();
        if (mbeanServer == null) {
            return null;
        }

        try {
            String jmxDomain = getCamelContext().getManagementStrategy().getManagementAgent().getMBeanObjectDomainName();
            String prefix = getCamelContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
            ObjectName query = ObjectName.getInstance(
                    jmxDomain + ":context=" + prefix + getCamelContext().getManagementName() + ",type=producers,*");
            Set<ObjectName> set = mbeanServer.queryNames(query, null);
            return (set != null && !set.isEmpty()) ? set : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ManagedProducerMBean getManagedProducer(CamelContext camelContext, ObjectName on) {
        return camelContext.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedProducerMBean.class);
    }

}
