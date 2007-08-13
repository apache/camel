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
import java.util.Hashtable;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;

public class CamelNamingStrategy {

	public static final String VALUE_UNKNOWN = "unknown";
	public static final String KEY_CONTEXT = "context";
	public static final String KEY_ENDPOINT = "endpoint";
	public static final String KEY_ROUTE = "route";
	public static final String KEY_TYPE = "type";
	public static final String KEY_NAME = "name";
	public static final String TYPE_ENDPOINTS = "Endpoints";
	public static final String TYPE_SERVICES = "Services";
	public static final String TYPE_ROUTES = "Routes";
	
	protected String domainName = "org.apache.camel";
	protected String hostName = "locahost";
	
	public CamelNamingStrategy(String domainName) {
		if (domainName != null) {
		    this.domainName = domainName;
		}
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException ex) {
			// ignore
		}
	}

	/**
	 * Implements the naming strategy for a {@see CamelContext}.
	 * The convention used for a {@see CamelContext} ObjectName is
	 * "<domain>:context=<context>,name=camel".
	 * 
	 * @param mbean
	 * @return generated ObjectName
	 * @throws MalformedObjectNameException
	 */
	public ObjectName getObjectName(CamelContext context) throws MalformedObjectNameException {
		Hashtable<String, String> keys = new Hashtable<String, String>();
		keys.put(KEY_CONTEXT, getContextId(context));
		keys.put(KEY_NAME, "camel");
		return new ObjectName(domainName, keys);
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
		Hashtable<String, String> keys = new Hashtable<String, String>();
		keys.put(KEY_CONTEXT, getContextId(ep.getContext()));
		keys.put(KEY_TYPE, TYPE_ENDPOINTS);
		keys.put(KEY_ENDPOINT, getEndpointId(ep));
		return new ObjectName(domainName, keys);
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
		Hashtable<String, String> keys = new Hashtable<String, String>();
		keys.put(KEY_CONTEXT, getContextId(context));
		keys.put(KEY_TYPE, TYPE_SERVICES);
		keys.put(KEY_ENDPOINT, Integer.toHexString(mbean.getService().hashCode()));
		return new ObjectName(domainName, keys);
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
		Hashtable<String, String> keys = new Hashtable<String, String>();
		Endpoint ep = mbean.getRoute().getEndpoint();
		String ctxid = ep != null ? getContextId(ep.getContext()) : VALUE_UNKNOWN;
		keys.put(KEY_CONTEXT, ctxid);
		keys.put(KEY_TYPE, TYPE_ROUTES);
		keys.put(KEY_ENDPOINT, getEndpointId(ep));
		return new ObjectName(domainName, keys);
	}
	
	protected String getContextId(CamelContext context) {
		String id = context != null ? Integer.toString(context.hashCode()) : VALUE_UNKNOWN;
		return hostName + "/" + id;
	}
	
	protected String getEndpointId(Endpoint ep) {
		String uri = ep.getEndpointUri();
		int pos = uri.indexOf(':');
		String id = (pos == -1) ? uri : 
			"[" + uri.substring(0, pos) + "]" + uri.substring(pos + 1);
		if (!ep.isSingleton()) { 
			id += "." + Integer.toString(ep.hashCode());
		}
		return id;
	}
}
