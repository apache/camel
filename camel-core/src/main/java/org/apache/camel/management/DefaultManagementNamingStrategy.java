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
package org.apache.camel.management;

import java.net.UnknownHostException;
import java.util.concurrent.ThreadPoolExecutor;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.ManagementNamingStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.InetAddressUtil;
import org.apache.camel.util.ObjectHelper;

/**
 * Naming strategy used when registering MBeans.
 */
public class DefaultManagementNamingStrategy implements ManagementNamingStrategy {
    public static final String VALUE_UNKNOWN = "unknown";
    public static final String KEY_NAME = "name";
    public static final String KEY_TYPE = "type";
    public static final String KEY_CONTEXT = "context";
    public static final String TYPE_CONTEXT = "context";
    public static final String TYPE_ENDPOINT = "endpoints";
    public static final String TYPE_PROCESSOR = "processors";
    public static final String TYPE_CONSUMER = "consumers";
    public static final String TYPE_PRODUCER = "producers";
    public static final String TYPE_ROUTE = "routes";
    public static final String TYPE_COMPONENT = "components";
    public static final String TYPE_TRACER = "tracer";
    public static final String TYPE_EVENT_NOTIFIER = "eventnotifiers";
    public static final String TYPE_ERRORHANDLER = "errorhandlers";
    public static final String TYPE_THREAD_POOL = "threadpools";
    public static final String TYPE_SERVICE = "services";

    protected String domainName;
    protected String hostName = "localhost";

    public DefaultManagementNamingStrategy() {
        this("org.apache.camel");
    }

    public DefaultManagementNamingStrategy(String domainName) {
        if (domainName != null) {
            this.domainName = domainName;
        }
        try {
            hostName = InetAddressUtil.getLocalHostName();
        } catch (UnknownHostException ex) {
            // ignore, use the default "localhost"
        }
    }

    public ObjectName getObjectNameForCamelContext(String name) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(name)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_CONTEXT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(name));
        return createObjectName(buffer);
    }

    public ObjectName getObjectNameForCamelContext(CamelContext context) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_CONTEXT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(context.getName()));
        return createObjectName(buffer);
    }

    public ObjectName getObjectNameForEndpoint(Endpoint endpoint) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(endpoint.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_ENDPOINT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(getEndpointId(endpoint)));
        return createObjectName(buffer);
    }

    public ObjectName getObjectNameForComponent(Component component, String name) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(component.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_COMPONENT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(name));
        return createObjectName(buffer);
    }

    public ObjectName getObjectNameForProcessor(CamelContext context, Processor processor, ProcessorDefinition<?> definition) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=").append(TYPE_PROCESSOR).append(",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(definition.getId()));
        return createObjectName(buffer);
    }

    public ObjectName getObjectNameForErrorHandler(RouteContext routeContext, Processor errorHandler, ErrorHandlerBuilder builder) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(routeContext.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=").append(TYPE_ERRORHANDLER).append(",");

        // we want to only register one instance of the various error handler types and thus do some lookup
        // if its a ErrorHandlerBuildRef. We need a bit of work to do that as there are potential indirection.
        String ref = null;
        if (builder instanceof ErrorHandlerBuilderRef) {
            ErrorHandlerBuilderRef builderRef = (ErrorHandlerBuilderRef) builder;

            // it has not then its an indirection and we should do some work to lookup the real builder
            ref = builderRef.getRef();
            builder = ErrorHandlerBuilderRef.lookupErrorHandlerBuilder(routeContext, builderRef.getRef());

            // must do a 2nd lookup in case this is also a reference
            // (this happens with spring DSL using errorHandlerRef on <route> as it gets a bit
            // complex with indirections for error handler references
            if (builder instanceof ErrorHandlerBuilderRef) {
                builderRef = (ErrorHandlerBuilderRef) builder;
                // does it refer to a non default error handler then do a 2nd lookup
                if (!builderRef.getRef().equals(ErrorHandlerBuilderRef.DEFAULT_ERROR_HANDLER_BUILDER)) {
                    builder = ErrorHandlerBuilderRef.lookupErrorHandlerBuilder(routeContext, builderRef.getRef());
                    ref = builderRef.getRef();
                }
            }
        }

        if (ref != null) {
            String name = builder.getClass().getSimpleName() + "(ref:" + ref + ")";
            buffer.append(KEY_NAME + "=").append(ObjectName.quote(name));
        } else {
            // create a name based on its instance
            buffer.append(KEY_NAME + "=")
                .append(builder.getClass().getSimpleName())
                .append("(").append(ObjectHelper.getIdentityHashCode(builder)).append(")");
        }

        return createObjectName(buffer);
    }

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

    public ObjectName getObjectNameForTracer(CamelContext context, InterceptStrategy tracer) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_TRACER + ",");
        buffer.append(KEY_NAME + "=")
            .append("Tracer")
            .append("(").append(ObjectHelper.getIdentityHashCode(tracer)).append(")");
        return createObjectName(buffer);
    }

    public ObjectName getObjectNameForEventNotifier(CamelContext context, EventNotifier eventNotifier) throws MalformedObjectNameException {
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

    public ObjectName getObjectNameForRoute(Route route) throws MalformedObjectNameException {
        Endpoint ep = route.getEndpoint();
        String id = route.getId();

        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(ep.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_ROUTE + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(id));
        return createObjectName(buffer);
    }

    public ObjectName getObjectNameForService(CamelContext context, Service service) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_SERVICE + ",");
        buffer.append(KEY_NAME + "=")
            .append(service.getClass().getSimpleName())
            .append("(").append(ObjectHelper.getIdentityHashCode(service)).append(")");
        return createObjectName(buffer);
    }

    public ObjectName getObjectNameForThreadPool(CamelContext context, ThreadPoolExecutor threadPool, String id, String sourceId) throws MalformedObjectNameException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_THREAD_POOL + ",");
        buffer.append(KEY_NAME + "=").append(id);
        if (sourceId != null) {
            // provide source id if we know it, this helps end user to know where the pool is used
            buffer.append("(").append(sourceId).append(")");
        }
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
        return hostName + "/" + (name != null ? name : VALUE_UNKNOWN);
    }

    protected String getEndpointId(Endpoint ep) {
        if (ep.isSingleton()) {
            return ep.getEndpointKey();
        } else {
            // non singleton then add hashcoded id
            String uri = ep.getEndpointKey();
            int pos = uri.indexOf('?');
            String id = (pos == -1) ? uri : uri.substring(0, pos);
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
