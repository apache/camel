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

package org.apache.camel.management;

import java.util.ArrayList;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedConsumerMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteGroupMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedStepMBean;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.ManagementStrategy;

/**
 * JMX capable {@link CamelContext}.
 */
public class ManagedCamelContextImpl implements ManagedCamelContext {

    private final CamelContext camelContext;

    public ManagedCamelContextImpl(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    private ManagementStrategy getManagementStrategy() {
        return camelContext.getManagementStrategy();
    }

    @Override
    public <T extends ManagedProcessorMBean> T getManagedProcessor(String id, Class<T> type) {
        // jmx must be enabled
        if (getManagementStrategy().getManagementAgent() == null) {
            return null;
        }

        Processor processor = camelContext.getProcessor(id);
        ProcessorDefinition<?> def = camelContext
                .getCamelContextExtension()
                .getContextPlugin(Model.class)
                .getProcessorDefinition(id);

        // processor may be null if its anonymous inner class or as lambda
        if (def != null) {
            try {
                ObjectName on = getManagementStrategy()
                        .getManagementObjectNameStrategy()
                        .getObjectNameForProcessor(camelContext, processor, def);
                return getManagementStrategy().getManagementAgent().newProxyClient(on, type);
            } catch (MalformedObjectNameException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return null;
    }

    @Override
    public ManagedStepMBean getManagedStep(String id) {
        // jmx must be enabled
        if (getManagementStrategy().getManagementAgent() == null) {
            return null;
        }

        Processor processor = camelContext.getProcessor(id);
        ProcessorDefinition<?> def = camelContext
                .getCamelContextExtension()
                .getContextPlugin(Model.class)
                .getProcessorDefinition(id);

        // processor may be null if its anonymous inner class or as lambda
        if (def != null) {
            try {
                ObjectName on = getManagementStrategy()
                        .getManagementObjectNameStrategy()
                        .getObjectNameForStep(camelContext, processor, def);
                return getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedStepMBean.class);
            } catch (MalformedObjectNameException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return null;
    }

    @Override
    public <T extends ManagedRouteMBean> T getManagedRoute(String routeId, Class<T> type) {
        // jmx must be enabled
        if (getManagementStrategy().getManagementAgent() == null) {
            return null;
        }

        Route route = camelContext.getRoute(routeId);
        if (route != null) {
            try {
                ObjectName on = getManagementStrategy()
                        .getManagementObjectNameStrategy()
                        .getObjectNameForRoute(route);
                return getManagementStrategy().getManagementAgent().newProxyClient(on, type);
            } catch (MalformedObjectNameException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return null;
    }

    @Override
    public List<ManagedRouteMBean> getManagedRoutes() {
        // null group will return all
        return getManagedRoutesByGroup(null);
    }

    @Override
    public ManagedRouteGroupMBean getManagedRouteGroup(String group) {
        // jmx must be enabled
        if (getManagementStrategy().getManagementAgent() == null) {
            return null;
        }

        if (group != null && !camelContext.getRoutesByGroup(group).isEmpty()) {
            try {
                ObjectName on = getManagementStrategy()
                        .getManagementObjectNameStrategy()
                        .getObjectNameForRouteGroup(camelContext, group);
                return getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedRouteGroupMBean.class);
            } catch (MalformedObjectNameException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return null;
    }

    @Override
    public List<ManagedRouteMBean> getManagedRoutesByGroup(String groupId) {
        // jmx must be enabled
        if (getManagementStrategy().getManagementAgent() == null) {
            return null;
        }

        List<ManagedRouteMBean> answer = new ArrayList<>();
        for (Route route : camelContext.getRoutes()) {
            if (groupId == null || groupId.equals(route.getGroup())) {
                try {
                    ObjectName on = getManagementStrategy()
                            .getManagementObjectNameStrategy()
                            .getObjectNameForRoute(route);
                    ManagedRouteMBean mr =
                            getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedRouteMBean.class);
                    answer.add(mr);
                } catch (MalformedObjectNameException e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }
        }
        return answer;
    }

    @Override
    public <T extends ManagedConsumerMBean> T getManagedConsumer(String id, Class<T> type) {
        // jmx must be enabled
        if (getManagementStrategy().getManagementAgent() == null) {
            return null;
        }

        Route route = camelContext.getRoute(id);
        if (route != null) {
            try {
                Consumer consumer = route.getConsumer();
                ObjectName on = getManagementStrategy()
                        .getManagementObjectNameStrategy()
                        .getObjectNameForConsumer(camelContext, consumer);
                return getManagementStrategy().getManagementAgent().newProxyClient(on, type);
            } catch (MalformedObjectNameException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return null;
    }

    @Override
    public ManagedCamelContextMBean getManagedCamelContext() {
        // jmx must be enabled
        if (getManagementStrategy().getManagementAgent() == null) {
            return null;
        }
        // jmx must be started
        if (getManagementStrategy().getManagementObjectNameStrategy() == null) {
            return null;
        }

        try {
            ObjectName on = getManagementStrategy()
                    .getManagementObjectNameStrategy()
                    .getObjectNameForCamelContext(camelContext);
            return getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedCamelContextMBean.class);
        } catch (MalformedObjectNameException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
