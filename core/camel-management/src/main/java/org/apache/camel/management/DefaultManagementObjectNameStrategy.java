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

import java.net.UnknownHostException;
import java.util.concurrent.ThreadPoolExecutor;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.StaticService;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.management.mbean.ManagedBacklogDebugger;
import org.apache.camel.management.mbean.ManagedBacklogTracer;
import org.apache.camel.management.mbean.ManagedCamelContext;
import org.apache.camel.management.mbean.ManagedCamelHealth;
import org.apache.camel.management.mbean.ManagedClusterService;
import org.apache.camel.management.mbean.ManagedComponent;
import org.apache.camel.management.mbean.ManagedConsumer;
import org.apache.camel.management.mbean.ManagedDataFormat;
import org.apache.camel.management.mbean.ManagedDumpRouteStrategy;
import org.apache.camel.management.mbean.ManagedEndpoint;
import org.apache.camel.management.mbean.ManagedEventNotifier;
import org.apache.camel.management.mbean.ManagedProcessor;
import org.apache.camel.management.mbean.ManagedProducer;
import org.apache.camel.management.mbean.ManagedRoute;
import org.apache.camel.management.mbean.ManagedRouteController;
import org.apache.camel.management.mbean.ManagedRouteGroup;
import org.apache.camel.management.mbean.ManagedService;
import org.apache.camel.management.mbean.ManagedStep;
import org.apache.camel.management.mbean.ManagedSupervisingRouteController;
import org.apache.camel.management.mbean.ManagedThreadPool;
import org.apache.camel.management.mbean.ManagedTracer;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementObjectNameStrategy;
import org.apache.camel.spi.RouteController;
import org.apache.camel.util.InetAddressUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * Naming strategy used when registering MBeans.
 */
public class DefaultManagementObjectNameStrategy implements ManagementObjectNameStrategy, CamelContextAware {
    public static final String VALUE_UNKNOWN = "unknown";
    public static final String KEY_NAME = "name";
    public static final String KEY_TYPE = "type";
    public static final String KEY_CONTEXT = "context";
    public static final String TYPE_CONTEXT = "context";
    public static final String TYPE_HEALTH = "health";
    public static final String TYPE_ENDPOINT = "endpoints";
    public static final String TYPE_DATAFORMAT = "dataformats";
    public static final String TYPE_PROCESSOR = "processors";
    public static final String TYPE_CONSUMER = "consumers";
    public static final String TYPE_PRODUCER = "producers";
    public static final String TYPE_ROUTE = "routes";
    public static final String TYPE_ROUTE_GROUP = "routegroups";
    public static final String TYPE_COMPONENT = "components";
    public static final String TYPE_STEP = "steps";
    public static final String TYPE_TRACER = "tracer";
    public static final String TYPE_EVENT_NOTIFIER = "eventnotifiers";
    public static final String TYPE_THREAD_POOL = "threadpools";
    public static final String TYPE_SERVICE = "services";
    public static final String TYPE_HA = "clusterservices";

    protected String domainName;
    protected String hostName = "localhost";
    protected CamelContext camelContext;

    public DefaultManagementObjectNameStrategy() {
        this(null);
        // default constructor needed for <bean> style configuration
    }

    public DefaultManagementObjectNameStrategy(String domainName) {
        this.domainName = domainName != null ? domainName : "org.apache.camel";
        try {
            hostName = InetAddressUtil.getLocalHostName();
        } catch (UnknownHostException ex) {
            // ignore, use the default "localhost"
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public ObjectName getObjectName(Object managedObject) throws MalformedObjectNameException {
        if (managedObject == null) {
            return null;
        }
        ObjectName objectName = null;
        if (managedObject instanceof ManagedCamelContext mcc) {
            objectName = getObjectNameForCamelContext(mcc.getContext());
        } else if (managedObject instanceof ManagedCamelHealth mch) {
            objectName = getObjectNameForCamelHealth(mch.getContext());
        } else if (managedObject instanceof ManagedRouteController mrc) {
            objectName = getObjectNameForRouteController(mrc.getContext(), mrc.getRouteController());
        } else if (managedObject instanceof ManagedSupervisingRouteController mrc) {
            objectName = getObjectNameForRouteController(mrc.getContext(), mrc.getRouteController());
        } else if (managedObject instanceof ManagedComponent mc) {
            objectName = getObjectNameForComponent(mc.getComponent(), mc.getComponentName());
        } else if (managedObject instanceof ManagedDataFormat md) {
            objectName = getObjectNameForDataFormat(md.getContext(), md.getDataFormat());
        } else if (managedObject instanceof ManagedEndpoint me) {
            objectName = getObjectNameForEndpoint(me.getEndpoint());
        } else if (managedObject instanceof Endpoint endpoint) {
            objectName = getObjectNameForEndpoint(endpoint);
        } else if (managedObject instanceof ManagedRoute mr) {
            objectName = getObjectNameForRoute(mr.getRoute());
        } else if (managedObject instanceof ManagedRouteGroup mrg) {
            objectName = getObjectNameForRouteGroup(mrg.getContext(), mrg.getRouteGroup());
        } else if (managedObject instanceof ManagedStep mp) {
            objectName = getObjectNameForStep(mp.getContext(), mp.getProcessor(), mp.getDefinition());
        } else if (managedObject instanceof ManagedProcessor mp) {
            objectName = getObjectNameForProcessor(mp.getContext(), mp.getProcessor(), mp.getDefinition());
        } else if (managedObject instanceof ManagedConsumer ms) {
            objectName = getObjectNameForConsumer(ms.getContext(), ms.getConsumer());
        } else if (managedObject instanceof ManagedProducer ms) {
            objectName = getObjectNameForProducer(ms.getContext(), ms.getProducer());
        } else if (managedObject instanceof ManagedBacklogTracer mt) {
            objectName = getObjectNameForTracer(mt.getContext(), mt.getBacklogTracer());
        } else if (managedObject instanceof ManagedBacklogDebugger md) {
            objectName = getObjectNameForTracer(md.getContext(), md.getBacklogDebugger());
        } else if (managedObject instanceof ManagedDumpRouteStrategy md) {
            objectName = getObjectNameForService(md.getContext(), md.getDumpRoutesStrategy());
        } else if (managedObject instanceof ManagedEventNotifier men) {
            objectName = getObjectNameForEventNotifier(men.getContext(), men.getEventNotifier());
        } else if (managedObject instanceof ManagedTracer mt) {
            objectName = getObjectNameForTracer(mt.getContext(), mt.getTracer());
        } else if (managedObject instanceof ManagedThreadPool mes) {
            objectName =
                    getObjectNameForThreadPool(mes.getContext(), mes.getThreadPool(), mes.getId(), mes.getSourceId());
        } else if (managedObject instanceof ManagedClusterService mcs) {
            objectName = getObjectNameForClusterService(mcs.getContext(), mcs.getService());
        } else if (managedObject instanceof ManagedService ms) {
            // check for managed service should be last
            // skip endpoints as they are already managed
            if (ms.getService() instanceof Endpoint) {
                return null;
            }
            objectName = getObjectNameForService(ms.getContext(), ms.getService());
        }

        return objectName;
    }

    @Override
    public ObjectName getObjectNameForCamelContext(String managementName, String name)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT)
                .append("=")
                .append(getContextId(managementName))
                .append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_CONTEXT).append(",");
        buffer.append(KEY_NAME).append("=").append(ObjectName.quote(name));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForCamelContext(CamelContext context) throws MalformedObjectNameException {
        // prefer to use the given management name if previously assigned
        String managementName = context.getManagementName();
        if (managementName == null) {
            managementName = context.getManagementNameStrategy().getName();
        }
        String name = context.getName();
        return getObjectNameForCamelContext(managementName, name);
    }

    @Override
    public ObjectName getObjectNameForCamelHealth(CamelContext context) throws MalformedObjectNameException {
        // prefer to use the given management name if previously assigned
        String managementName = context.getManagementName();
        if (managementName == null) {
            managementName = context.getManagementNameStrategy().getName();
        }

        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT)
                .append("=")
                .append(getContextId(managementName))
                .append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_HEALTH).append(",");
        buffer.append(KEY_NAME).append("=").append("DefaultHealthCheck");

        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForRouteController(CamelContext context, RouteController routeController)
            throws MalformedObjectNameException {
        // prefer to use the given management name if previously assigned
        String managementName = context.getManagementName();
        if (managementName == null) {
            managementName = context.getManagementNameStrategy().getName();
        }

        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT)
                .append("=")
                .append(getContextId(managementName))
                .append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_SERVICE).append(",");
        buffer.append(KEY_NAME).append("=").append(routeController.getClass().getSimpleName());

        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForEndpoint(Endpoint endpoint) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT)
                .append("=")
                .append(getContextId(endpoint.getCamelContext()))
                .append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_ENDPOINT).append(",");
        buffer.append(KEY_NAME).append("=").append(ObjectName.quote(getEndpointId(endpoint)));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForDataFormat(CamelContext context, DataFormat dataFormat)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_DATAFORMAT).append(",");
        buffer.append(KEY_NAME).append("=").append(dataFormat.getClass().getSimpleName());
        if (!(dataFormat instanceof StaticService)) {
            buffer.append("(")
                    .append(ObjectHelper.getIdentityHashCode(dataFormat))
                    .append(")");
        }
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForComponent(Component component, String name) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT)
                .append("=")
                .append(getContextId(component.getCamelContext()))
                .append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_COMPONENT).append(",");
        buffer.append(KEY_NAME).append("=").append(ObjectName.quote(name));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForProcessor(CamelContext context, Processor processor, NamedNode definition)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_PROCESSOR).append(",");
        String id = definition.getId();
        String prefix = definition.getNodePrefixId();
        if (prefix != null) {
            id = prefix + id;
        }
        buffer.append(KEY_NAME).append("=").append(ObjectName.quote(id));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForStep(CamelContext context, Processor processor, NamedNode definition)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_STEP).append(",");
        String id = definition.getId();
        String prefix = definition.getNodePrefixId();
        if (prefix != null) {
            id = prefix + id;
        }
        buffer.append(KEY_NAME).append("=").append(ObjectName.quote(id));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForConsumer(CamelContext context, Consumer consumer)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_CONSUMER).append(",");

        String name = consumer.getClass().getSimpleName();
        if (ObjectHelper.isEmpty(name)) {
            name = "Consumer";
        }
        buffer.append(KEY_NAME)
                .append("=")
                .append(name)
                .append("(")
                .append(ObjectHelper.getIdentityHashCode(consumer))
                .append(")");
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForProducer(CamelContext context, Producer producer)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_PRODUCER).append(",");

        String name = producer.getClass().getSimpleName();
        if (ObjectHelper.isEmpty(name)) {
            name = "Producer";
        }
        buffer.append(KEY_NAME + "=")
                .append(name)
                .append("(")
                .append(ObjectHelper.getIdentityHashCode(producer))
                .append(")");
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForTracer(CamelContext context, Service tracer) throws MalformedObjectNameException {
        // use the simple name of the class as the mbean name (eg Tracer, BacklogTracer, BacklogDebugger)
        String name = tracer.getClass().getSimpleName();
        // backwards compatible names
        if ("DefaultBacklogDebugger".equals(name)) {
            name = "BacklogDebugger";
        } else if ("DefaultBacklogTracer".equals(name)) {
            name = "BacklogTracer";
        }

        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_TRACER).append(",");
        buffer.append(KEY_NAME).append("=").append(name);
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForEventNotifier(CamelContext context, EventNotifier eventNotifier)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_EVENT_NOTIFIER).append(",");

        if (eventNotifier instanceof JmxNotificationEventNotifier) {
            // JMX notifier shall have an easy to use name
            buffer.append(KEY_NAME).append("=").append("JmxEventNotifier");
        } else {
            // others can be per instance
            buffer.append(KEY_NAME)
                    .append("=")
                    .append("EventNotifier")
                    .append("(")
                    .append(ObjectHelper.getIdentityHashCode(eventNotifier))
                    .append(")");
        }
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForRoute(org.apache.camel.Route route) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT)
                .append("=")
                .append(getContextId(route.getCamelContext()))
                .append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_ROUTE).append(",");
        buffer.append(KEY_NAME).append("=").append(ObjectName.quote(route.getId()));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForRouteGroup(CamelContext camelContext, String group)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT)
                .append("=")
                .append(getContextId(camelContext))
                .append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_ROUTE_GROUP).append(",");
        buffer.append(KEY_NAME).append("=").append(ObjectName.quote(group));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForService(CamelContext context, Service service)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_SERVICE).append(",");
        buffer.append(KEY_NAME).append("=").append(service.getClass().getSimpleName());
        if (!(service instanceof StaticService)) {
            buffer.append("(").append(ObjectHelper.getIdentityHashCode(service)).append(")");
        }
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForClusterService(CamelContext context, CamelClusterService service)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_HA).append(",");
        buffer.append(KEY_NAME).append("=").append(service.getClass().getSimpleName());
        if (!(service instanceof StaticService)) {
            buffer.append("(").append(ObjectHelper.getIdentityHashCode(service)).append(")");
        }
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForThreadPool(
            CamelContext context, ThreadPoolExecutor threadPool, String id, String sourceId)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT).append("=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE).append("=").append(TYPE_THREAD_POOL).append(",");

        String name = id;
        if (sourceId != null) {
            // provide source id if we know it, this helps end user to know where the pool is used
            name = name + "(" + sourceId + ")";
        }
        buffer.append(KEY_NAME).append("=").append(ObjectName.quote(name));
        return createObjectName(buffer);
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    protected String getContextId(CamelContext context) {
        if (context == null) {
            return getContextId(VALUE_UNKNOWN);
        } else {
            String name = context.getManagementName() != null ? context.getManagementName() : context.getName();
            return getContextId(name);
        }
    }

    protected String getContextId(String name) {
        boolean includeHostName = camelContext != null
                && camelContext.getManagementStrategy().getManagementAgent().getIncludeHostName();
        if (includeHostName) {
            return hostName + "/" + (name != null ? name : VALUE_UNKNOWN);
        } else {
            return name != null ? name : VALUE_UNKNOWN;
        }
    }

    protected String getEndpointId(Endpoint ep) {
        String answer = doGetEndpointId(ep);
        boolean sanitize = camelContext != null
                && camelContext.getManagementStrategy().getManagementAgent().getMask();
        if (sanitize) {
            // use xxxxxx as replacements as * has to be quoted for MBean names
            answer = URISupport.sanitizeUri(answer);
        }
        return answer;
    }

    private String doGetEndpointId(Endpoint ep) {
        if (ep.isSingleton()) {
            return ep.getEndpointKey();
        } else {
            // non singleton then add hashcoded id
            String uri = ep.getEndpointKey();
            String id = StringHelper.before(uri, "?", uri);
            id += "?id=" + ObjectHelper.getIdentityHashCode(ep);
            return id;
        }
    }

    /**
     * Factory method to create an ObjectName escaping any required characters
     */
    protected ObjectName createObjectName(StringBuilder buffer) throws MalformedObjectNameException {
        String text = buffer.toString();
        try {
            return new ObjectName(text);
        } catch (MalformedObjectNameException e) {
            throw new MalformedObjectNameException("Could not create ObjectName from: " + text + ". Reason: " + e);
        }
    }
}
