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

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.management.mbean.ManagedComponent;
import org.apache.camel.management.mbean.ManagedConsumer;
import org.apache.camel.management.mbean.ManagedEndpoint;
import org.apache.camel.management.mbean.ManagedProcessor;
import org.apache.camel.management.mbean.ManagedRoute;
import org.apache.camel.management.mbean.ManagedService;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.ManagementNamingStrategy;
import org.apache.camel.spi.RouteContext;

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
    public static final String TYPE_ROUTE = "routes";
    public static final String TYPE_COMPONENT = "components";

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
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            // ignore, use the default "localhost"
        }
    }

    public ObjectName getObjectName(CamelContext context) throws MalformedObjectNameException {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_CONTEXT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(context.getName()));
        return createObjectName(buffer);
    }

    public ObjectName getObjectName(ManagedEndpoint mbean) throws MalformedObjectNameException {
        Endpoint ep = mbean.getEndpoint();

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(ep.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_ENDPOINT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(getEndpointId(ep)));
        return createObjectName(buffer);
    }

    public ObjectName getObjectName(ManagedComponent mbean) throws MalformedObjectNameException {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(mbean.getComponent().getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_COMPONENT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(mbean.getComponentName()));
        return createObjectName(buffer);
    }

    public ObjectName getObjectName(ManagedProcessor mbean) throws MalformedObjectNameException {
        Processor processor = mbean.getProcessor();
        ProcessorDefinition definition = mbean.getDefinition();

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(mbean.getContext())).append(",");
        buffer.append(KEY_TYPE + "=").append(TYPE_PROCESSOR).append(",");

        if (definition.hasCustomIdAssigned()) {
            // use id in name
            String nodeId = definition.getId();
            buffer.append(KEY_NAME + "=").append(ObjectName.quote(nodeId));
        } else {
            // create a name based on its instance
            buffer.append(KEY_NAME + "=")
                .append(processor.getClass().getSimpleName())
                .append("(").append(getIdentityHashCode(processor)).append(")");
        }
        return createObjectName(buffer);
    }

    public ObjectName getObjectName(ManagedConsumer mbean) throws MalformedObjectNameException {
        Consumer consumer = mbean.getConsumer();

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(mbean.getContext())).append(",");
        buffer.append(KEY_TYPE + "=").append(TYPE_CONSUMER).append(",");
        buffer.append(KEY_NAME + "=")
            .append(consumer.getClass().getSimpleName())
            .append("(").append(getIdentityHashCode(consumer)).append(")");
        return createObjectName(buffer);
    }

    public ObjectName getObjectName(ManagedService mbean) throws MalformedObjectNameException {
        // not supported
        return null;
    }

    public ObjectName getObjectName(ManagedRoute mbean) throws MalformedObjectNameException {
        Route route = mbean.getRoute();
        Endpoint ep = route.getEndpoint();
        String id = route.getId();

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(ep.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_ROUTE + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(id));
        return createObjectName(buffer);
    }

    @Deprecated
    public ObjectName getObjectName(RouteContext routeContext, ProcessorDefinition processor)
        throws MalformedObjectNameException {

        Endpoint ep = routeContext.getEndpoint();
        String nodeId = processor.idOrCreate(routeContext.getCamelContext().getNodeIdFactory());

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(ep.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_PROCESSOR + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(nodeId));
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
        return hostName + "/" + (context != null ? context.getName() : VALUE_UNKNOWN);
    }

    protected String getEndpointId(Endpoint ep) {
        if (ep.isSingleton()) {
            return ep.getEndpointKey();
        } else {
            // non singleton then add hashcoded id
            String uri = ep.getEndpointKey();
            int pos = uri.indexOf('?');
            String id = (pos == -1) ? uri : uri.substring(0, pos);
            id += "?id=" + getIdentityHashCode(ep);
            return id;
        }
    }

    private static String getIdentityHashCode(Object object) {
        return "0x" + Integer.toHexString(System.identityHashCode(object));
    }

    /**
     * Factory method to create an ObjectName escaping any required characters
     */
    protected ObjectName createObjectName(StringBuffer buffer) throws MalformedObjectNameException {
        String text = buffer.toString();
        try {
            return new ObjectName(text);
        } catch (MalformedObjectNameException e) {
            throw new MalformedObjectNameException("Could not create ObjectName from: " + text + ". Reason: " + e);
        }
    }
}
