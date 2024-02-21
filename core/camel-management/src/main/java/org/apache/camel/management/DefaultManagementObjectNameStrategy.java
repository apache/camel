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
        if (managedObject instanceof ManagedCamelContext) {
            ManagedCamelContext mcc = (ManagedCamelContext) managedObject;
            objectName = getObjectNameForCamelContext(mcc.getContext());
        } else if (managedObject instanceof ManagedCamelHealth) {
            ManagedCamelHealth mch = (ManagedCamelHealth) managedObject;
            objectName = getObjectNameForCamelHealth(mch.getContext());
        } else if (managedObject instanceof ManagedRouteController) {
            ManagedRouteController mrc = (ManagedRouteController) managedObject;
            objectName = getObjectNameForRouteController(mrc.getContext(), mrc.getRouteController());
        } else if (managedObject instanceof ManagedSupervisingRouteController) {
            ManagedSupervisingRouteController mrc = (ManagedSupervisingRouteController) managedObject;
            objectName = getObjectNameForRouteController(mrc.getContext(), mrc.getRouteController());
        } else if (managedObject instanceof ManagedComponent) {
            ManagedComponent mc = (ManagedComponent) managedObject;
            objectName = getObjectNameForComponent(mc.getComponent(), mc.getComponentName());
        } else if (managedObject instanceof ManagedDataFormat) {
            ManagedDataFormat md = (ManagedDataFormat) managedObject;
            objectName = getObjectNameForDataFormat(md.getContext(), md.getDataFormat());
        } else if (managedObject instanceof ManagedEndpoint) {
            ManagedEndpoint me = (ManagedEndpoint) managedObject;
            objectName = getObjectNameForEndpoint(me.getEndpoint());
        } else if (managedObject instanceof Endpoint) {
            objectName = getObjectNameForEndpoint((Endpoint) managedObject);
        } else if (managedObject instanceof ManagedRoute) {
            ManagedRoute mr = (ManagedRoute) managedObject;
            objectName = getObjectNameForRoute(mr.getRoute());
        } else if (managedObject instanceof ManagedStep) {
            ManagedStep mp = (ManagedStep) managedObject;
            objectName = getObjectNameForStep(mp.getContext(), mp.getProcessor(), mp.getDefinition());
        } else if (managedObject instanceof ManagedProcessor) {
            ManagedProcessor mp = (ManagedProcessor) managedObject;
            objectName = getObjectNameForProcessor(mp.getContext(), mp.getProcessor(), mp.getDefinition());
        } else if (managedObject instanceof ManagedConsumer) {
            ManagedConsumer ms = (ManagedConsumer) managedObject;
            objectName = getObjectNameForConsumer(ms.getContext(), ms.getConsumer());
        } else if (managedObject instanceof ManagedProducer) {
            ManagedProducer ms = (ManagedProducer) managedObject;
            objectName = getObjectNameForProducer(ms.getContext(), ms.getProducer());
        } else if (managedObject instanceof ManagedBacklogTracer) {
            ManagedBacklogTracer mt = (ManagedBacklogTracer) managedObject;
            objectName = getObjectNameForTracer(mt.getContext(), mt.getBacklogTracer());
        } else if (managedObject instanceof ManagedBacklogDebugger) {
            ManagedBacklogDebugger md = (ManagedBacklogDebugger) managedObject;
            objectName = getObjectNameForTracer(md.getContext(), md.getBacklogDebugger());
        } else if (managedObject instanceof ManagedDumpRouteStrategy) {
            ManagedDumpRouteStrategy md = (ManagedDumpRouteStrategy) managedObject;
            objectName = getObjectNameForService(md.getContext(), md.getDumpRoutesStrategy());
        } else if (managedObject instanceof ManagedEventNotifier) {
            ManagedEventNotifier men = (ManagedEventNotifier) managedObject;
            objectName = getObjectNameForEventNotifier(men.getContext(), men.getEventNotifier());
        } else if (managedObject instanceof ManagedTracer) {
            ManagedTracer mt = (ManagedTracer) managedObject;
            objectName = getObjectNameForTracer(mt.getContext(), mt.getTracer());
        } else if (managedObject instanceof ManagedThreadPool) {
            ManagedThreadPool mes = (ManagedThreadPool) managedObject;
            objectName = getObjectNameForThreadPool(mes.getContext(), mes.getThreadPool(), mes.getId(), mes.getSourceId());
        } else if (managedObject instanceof ManagedClusterService) {
            ManagedClusterService mcs = (ManagedClusterService) managedObject;
            objectName = getObjectNameForClusterService(mcs.getContext(), mcs.getService());
        } else if (managedObject instanceof ManagedService) {
            // check for managed service should be last
            ManagedService ms = (ManagedService) managedObject;
            // skip endpoints as they are already managed
            if (ms.getService() instanceof Endpoint) {
                return null;
            }
            objectName = getObjectNameForService(ms.getContext(), ms.getService());
        }

        return objectName;
    }

    @Override
    public ObjectName getObjectNameForCamelContext(String managementName, String name) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(managementName)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_CONTEXT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(name));
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
        buffer.append(KEY_CONTEXT + "=").append(getContextId(managementName)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_HEALTH + ",");
        buffer.append(KEY_NAME + "=").append("DefaultHealthCheck");

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
        buffer.append(KEY_CONTEXT + "=").append(getContextId(managementName)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_SERVICE + ",");
        buffer.append(KEY_NAME + "=").append(routeController.getClass().getSimpleName());

        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForEndpoint(Endpoint endpoint) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(endpoint.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_ENDPOINT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(getEndpointId(endpoint)));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForDataFormat(CamelContext context, DataFormat dataFormat)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_DATAFORMAT + ",");
        buffer.append(KEY_NAME + "=").append(dataFormat.getClass().getSimpleName());
        if (!(dataFormat instanceof StaticService)) {
            buffer.append("(").append(ObjectHelper.getIdentityHashCode(dataFormat)).append(")");
        }
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForComponent(Component component, String name) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(component.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_COMPONENT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(name));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForProcessor(CamelContext context, Processor processor, NamedNode definition)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=").append(TYPE_PROCESSOR).append(",");
        String id = definition.getId();
        String prefix = definition.getNodePrefixId();
        if (prefix != null) {
            id = prefix + id;
        }
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(id));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForStep(CamelContext context, Processor processor, NamedNode definition)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=").append(TYPE_STEP).append(",");
        String id = definition.getId();
        String prefix = definition.getNodePrefixId();
        if (prefix != null) {
            id = prefix + id;
        }
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(id));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForConsumer(CamelContext context, Consumer consumer) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=").append(TYPE_CONSUMER).append(",");

        String name = consumer.getClass().getSimpleName();
        if (ObjectHelper.isEmpty(name)) {
            name = "Consumer";
        }
        buffer.append(KEY_NAME + "=")
                .append(name)
                .append("(").append(ObjectHelper.getIdentityHashCode(consumer)).append(")");
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForProducer(CamelContext context, Producer producer) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=").append(TYPE_PRODUCER).append(",");

        String name = producer.getClass().getSimpleName();
        if (ObjectHelper.isEmpty(name)) {
            name = "Producer";
        }
        buffer.append(KEY_NAME + "=")
                .append(name)
                .append("(").append(ObjectHelper.getIdentityHashCode(producer)).append(")");
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
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_TRACER + ",");
        buffer.append(KEY_NAME + "=").append(name);
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForEventNotifier(CamelContext context, EventNotifier eventNotifier)
            throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_EVENT_NOTIFIER + ",");

        if (eventNotifier instanceof JmxNotificationEventNotifier) {
            // JMX notifier shall have an easy to use name
            buffer.append(KEY_NAME + "=").append("JmxEventNotifier");
        } else {
            // others can be per instance
            buffer.append(KEY_NAME + "=")
                    .append("EventNotifier")
                    .append("(").append(ObjectHelper.getIdentityHashCode(eventNotifier)).append(")");
        }
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForRoute(org.apache.camel.Route route) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(route.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_ROUTE + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(route.getId()));
        return createObjectName(buffer);
    }

    @Override
    public ObjectName getObjectNameForService(CamelContext context, Service service) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_SERVICE + ",");
        buffer.append(KEY_NAME + "=").append(service.getClass().getSimpleName());
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
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_HA + ",");
        buffer.append(KEY_NAME + "=").append(service.getClass().getSimpleName());
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
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_THREAD_POOL + ",");

        String name = id;
        if (sourceId != null) {
            // provide source id if we know it, this helps end user to know where the pool is used
            name = name + "(" + sourceId + ")";
        }
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(name));
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
        boolean includeHostName
                = camelContext != null && camelContext.getManagementStrategy().getManagementAgent().getIncludeHostName();
        if (includeHostName) {
            return hostName + "/" + (name != null ? name : VALUE_UNKNOWN);
        } else {
            return name != null ? name : VALUE_UNKNOWN;
        }
    }

    protected String getEndpointId(Endpoint ep) {
        String answer = doGetEndpointId(ep);
        boolean sanitize = camelContext != null && camelContext.getManagementStrategy().getManagementAgent().getMask();
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
