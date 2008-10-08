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
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.spi.RouteContext;

/**
 * Naming strategy used when registering MBeans.
 */
public class CamelNamingStrategy {
    public static final String VALUE_UNKNOWN = "unknown";
    public static final String KEY_NAME = "name";
    public static final String KEY_TYPE = "type";
    public static final String KEY_CONTEXT = "context";
    public static final String KEY_GROUP = "group";
    public static final String KEY_ROUTE = "route";
    public static final String KEY_NODE_ID = "nodeid";
    public static final String TYPE_CONTEXT = "context";
    public static final String TYPE_ENDPOINT = "endpoints";
    public static final String TYPE_PROCESSOR = "processors";
    public static final String TYPE_CONSUMER = "consumers";
    public static final String TYPE_ROUTE = "routes";

    protected String domainName;
    protected String hostName = "locahost";

    public CamelNamingStrategy() {
        this("org.apache.camel");
    }

    public CamelNamingStrategy(String domainName) {
        if (domainName != null) {
            this.domainName = domainName;
        }
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            // ignore, use the default "locahost"
        }
    }

    /**
     * Implements the naming strategy for a {@link CamelContext}.
     * The convention used for a {@link CamelContext} ObjectName is:
     * <tt>&lt;domain&gt;:context=&lt;context-name&gt;,type=context,name=&lt;context-name&gt;</tt>
     *
     * @param context the camel context
     * @return generated ObjectName
     * @throws MalformedObjectNameException
     */
    public ObjectName getObjectName(CamelContext context) throws MalformedObjectNameException {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_NAME + "=").append("context");
        return createObjectName(buffer);
    }

    /**
     * Implements the naming strategy for a {@link ManagedEndpoint}.
     * The convention used for a {@link ManagedEndpoint} ObjectName is:
     * <tt>&lt;domain&gt;:context=&lt;context-name&gt;,type=endpoint,component=&lt;component-name&gt;name=&lt;endpoint-name&gt;</tt>
     */
    public ObjectName getObjectName(ManagedEndpoint mbean) throws MalformedObjectNameException {
        Endpoint<? extends Exchange> ep = mbean.getEndpoint();

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(ep.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_ENDPOINT + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(getEndpointId(ep)));
        return createObjectName(buffer);
    }

    /**
     * Implements the naming strategy for a {@link org.apache.camel.impl.ServiceSupport Service}.
     * The convention used for a {@link org.apache.camel.Service Service} ObjectName is
     * <tt>&lt;domain&gt;:context=&lt;context-name&gt;,type=service,name=&lt;service-name&gt;</tt>
     */
    public ObjectName getObjectName(CamelContext context, ManagedService mbean) throws MalformedObjectNameException {
        String serviceBranch;
        Service service = mbean.getService();
        if (service instanceof Consumer) {
            serviceBranch = TYPE_CONSUMER;
        } else {
            return null;
        }
        
        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(context)).append(",");
        buffer.append(KEY_TYPE + "=" + serviceBranch + ",");
        buffer.append(KEY_NAME + "=")
            .append(service.getClass().getSimpleName())
            .append("(0x").append(Integer.toHexString(mbean.getService().hashCode())).append(")");
        return createObjectName(buffer);
    }


    /**
     * Implements the naming strategy for a {@link ManagedRoute}.
     * The convention used for a {@link ManagedRoute} ObjectName is:
     * <tt>&lt;domain&gt;:context=&lt;context-name&gt;,route=&lt;route-name&gt;,type=route,name=&lt;route-name&gt;</tt>
     */
    public ObjectName getObjectName(ManagedRoute mbean) throws MalformedObjectNameException {
        Route<? extends Exchange> route = mbean.getRoute();
        Endpoint<? extends Exchange> ep = route.getEndpoint();
        String id = (String)route.getProperties().get(Route.ID_PROPERTY);

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(getContextId(ep.getCamelContext())).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_ROUTE + ",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(id == null ? ("0x" + Integer.toHexString(route.hashCode())) : id));
        return createObjectName(buffer);
    }

    /**
     * Implements the naming strategy for a {@link ProcessorType}.
     * The convention used for a {@link ProcessorType} ObjectName is:
     * <tt>&lt;domain&gt;:context=&lt;context-name&gt;,route=&lt;route-name&gt;,type=processor,name=&lt;processor-name&gt;,nodeid=&lt;node-id&gt;</tt>
     */
    public ObjectName getObjectName(RouteContext routeContext, ProcessorType processor)
        throws MalformedObjectNameException {
        Endpoint<? extends Exchange> ep = routeContext.getEndpoint();
        String ctxid = ep != null ? getContextId(ep.getCamelContext()) : VALUE_UNKNOWN;
        String cid = ObjectName.quote(ep.getEndpointUri());
        //String id = VALUE_UNKNOWN.equals(cid) ? ObjectName.quote(getEndpointId(ep) : "[" + cid + "]" + ObjectName.quote(getEndpointId(ep);
        String nodeId = processor.idOrCreate();

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName).append(":");
        buffer.append(KEY_CONTEXT + "=").append(ctxid).append(",");
        // buffer.append(KEY_ROUTE + "=").append(id).append(",");
        buffer.append(KEY_TYPE + "=" + TYPE_PROCESSOR + ",");
        buffer.append(KEY_NODE_ID + "=").append(nodeId).append(",");
        buffer.append(KEY_NAME + "=").append(ObjectName.quote(processor.toString()));
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

    protected String getEndpointId(Endpoint<? extends Exchange> ep) {
        String uri = ep.getEndpointUri();
        int pos = uri.indexOf('?');
        String id = (pos == -1) ? uri : uri.substring(0, pos);
        id += "?id=0x" + Integer.toHexString(ep.hashCode());
        return id;
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
