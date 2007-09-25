/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.camel.Endpoint;

public class CamelNamingStrategy {
    public static final String VALUE_UNKNOWN = "unknown";
    public static final String VALUE_ROUTE = "route";
    public static final String KEY_NAME = "name";
    public static final String KEY_TYPE = "type";
    public static final String KEY_CONTEXT = "context";
    public static final String KEY_GROUP = "group";
    public static final String KEY_COMPONENT = "component";
    public static final String KEY_ROUTE_TYPE = "routeType";
    public static final String KEY_ROUTE = "route";
    public static final String GROUP_ENDPOINTS = "endpoints";
    public static final String GROUP_SERVICES = "services";
    public static final String GROUP_ROUTE_BUILDER = "routeBuilder";
    public static final String GROUP_ROUTE_TYPE = "routeType";
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
        }
        catch (UnknownHostException ex) {
            // ignore, use the default "locahost"
        }
    }

    /**
     * Implements the naming strategy for a {@see CamelContext}.
     * The convention used for a {@see CamelContext} ObjectName is
     * "<domain>:context=<context>,name=camel".
     *
     * @param context the camel context
     * @return generated ObjectName
     * @throws MalformedObjectNameException
     */
    public ObjectName getObjectName(CamelContext context) throws MalformedObjectNameException {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName + ":");
        buffer.append(KEY_CONTEXT + "=" + getContextId(context) + ",");
        buffer.append(KEY_NAME + "=" + "context");
        return createObjectName(buffer);
    }

    /**
     * Implements the naming strategy for a {@see ManagedEndpoint}.
     * The convention used for a {@see ManagedEndpoint} ObjectName is
     * "<domain>:context=<context>,type=Endpoints,endpoint=[urlPrefix]localPart".
     *
     * @param mbean
     * @return generated ObjectName
     * @throws MalformedObjectNameException
     */
    public ObjectName getObjectName(ManagedEndpoint mbean) throws MalformedObjectNameException {
        Endpoint ep = mbean.getEndpoint();

        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName + ":");
        buffer.append(KEY_CONTEXT + "=" + getContextId(ep.getContext()) + ",");
        buffer.append(KEY_GROUP + "=" + GROUP_ENDPOINTS + ",");
        buffer.append(KEY_COMPONENT + "=" + getComponentId(ep) + ",");
        buffer.append(KEY_NAME + "=" + getEndpointId(ep));
        return createObjectName(buffer);
    }

    /**
     * Implements the naming strategy for a {@see ServiceSpport Service}.
     * The convention used for a {@see Service} ObjectName is
     * "<domain>:context=<context>,type=Services,endpoint=[urlPrefix]localPart".
     *
     * @param mbean
     * @return generated ObjectName
     * @throws MalformedObjectNameException
     */
    public ObjectName getObjectName(CamelContext context, ManagedService mbean) throws MalformedObjectNameException {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName + ":");
        buffer.append(KEY_CONTEXT + "=" + getContextId(context) + ",");
        buffer.append(KEY_GROUP + "=" + GROUP_SERVICES + ",");
        buffer.append(KEY_NAME + "=" + Integer.toHexString(mbean.getService().hashCode()));
        return createObjectName(buffer);
    }


    /**
     * Implements the naming strategy for a {@see ManagedRoute}.
     * The convention used for a {@see ManagedEndpoint} ObjectName is
     * "<domain>:context=<context>,type=Routes,endpoint=[urlPrefix]localPart".
     *
     * @param mbean
     * @return generated ObjectName
     * @throws MalformedObjectNameException
     */
    public ObjectName getObjectName(ManagedRoute mbean) throws MalformedObjectNameException {
        Endpoint ep = mbean.getRoute().getEndpoint();
        String ctxid = ep != null ? getContextId(ep.getContext()) : VALUE_UNKNOWN;
        String cid = getComponentId(ep);
        String id = VALUE_UNKNOWN.equals(cid) ? getEndpointId(ep) : 
        	"[" + cid + "]" + getEndpointId(ep);
        
        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName + ":");
        buffer.append(KEY_CONTEXT + "=" + ctxid + ",");
        buffer.append(KEY_GROUP + "=" + GROUP_ROUTE_BUILDER + ",");
        buffer.append(KEY_ROUTE_TYPE + "=" + GROUP_ROUTE_TYPE + ",");
        buffer.append(KEY_ROUTE + "=" + id + ",");
        buffer.append(KEY_NAME + "=" + VALUE_ROUTE);
        return createObjectName(buffer);
    }

    /**
     * Implements the naming strategy for a {@see PerformanceCounter}.
     * The convention used for a {@see ManagedEndpoint} ObjectName is
     * "<domain>:context=<context>,type=Routes,endpoint=[urlPrefix]localPart".
     *
     * @param mbean
     * @return generated ObjectName
     * @throws MalformedObjectNameException
     */
    public ObjectName getObjectName(CamelContext context, PerformanceCounter mbean) throws MalformedObjectNameException {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domainName + ":");
        buffer.append(KEY_CONTEXT + "=" + getContextId(context) + ",");
        buffer.append(KEY_GROUP + "=" + GROUP_ENDPOINTS + ",");
        buffer.append(KEY_ROUTE + "=" + "Route.Counter" + ",");
        buffer.append(KEY_NAME + "=" + "Stats");
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
        String id = context != null ? context.getName() : VALUE_UNKNOWN;
        return hostName + "/" + id;
    }

    protected String getComponentId(Endpoint ep) {
        String uri = ep.getEndpointUri();
        int pos = uri.indexOf(':');
        return (pos == -1) ? VALUE_UNKNOWN : uri.substring(0, pos);
    }
    
    protected String getEndpointId(Endpoint ep) {
        String uri = ep.getEndpointUri();
        int pos = uri.indexOf(':');
        String id = (pos == -1) ? uri : uri.substring(pos + 1);
		if (!ep.isSingleton()) { 
			id += "." + Integer.toString(ep.hashCode());
		}
        id = id.replace("=", "_eq_");
        id = id.replace(",", "_cm_");
        return id;
	}

    /**
     * Factory method to create an ObjectName escaping any required characters
     */
    protected ObjectName createObjectName(StringBuffer buffer) throws MalformedObjectNameException {
        String text = buffer.toString();
        try {
            text = text.replace("?", "_qe_");
            return new ObjectName(text);
        }
        catch (MalformedObjectNameException e) {
            throw new MalformedObjectNameException("Could not create ObjectName from: " + text + ". Reason: " + e);
        }
    }
}
