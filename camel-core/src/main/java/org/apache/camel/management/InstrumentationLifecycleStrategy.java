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

import java.util.Collection;

import javax.management.JMException;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.InstrumentationAgent;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.RouteType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class InstrumentationLifecycleStrategy implements LifecycleStrategy {
    private static final transient Log LOG = LogFactory.getLog(InstrumentationProcessor.class);

    private InstrumentationAgent agent;
    private CamelNamingStrategy namingStrategy;

    public InstrumentationLifecycleStrategy(InstrumentationAgent agent) {
		this.agent = agent;
        setNamingStrategy(agent.getNamingStrategy());
    }
	
	public void onContextCreate(CamelContext context) {
		if (context instanceof DefaultCamelContext) {
			try {	
				DefaultCamelContext dc = (DefaultCamelContext)context;
				ManagedService ms = new ManagedService(dc);
                agent.register(ms, getNamingStrategy().getObjectName(dc));
			}
			catch(JMException e) {
				LOG.warn("Could not register CamelContext MBean", e);
			}
		}
	}
	
	public void onEndpointAdd(Endpoint endpoint) {
		try {
			ManagedEndpoint me = new ManagedEndpoint(endpoint);
			agent.register(me, getNamingStrategy().getObjectName(me));
		}
		catch(JMException e) {
			LOG.warn("Could not register Endpoint MBean", e);
		}
	}

	public void onRoutesAdd(Collection<Route> routes) {
		for (Route route: routes) {
			try {
				ManagedRoute mr = new ManagedRoute(route);
				agent.register(mr, getNamingStrategy().getObjectName(mr));
			}
			catch(JMException e) {
				LOG.warn("Could not register Route MBean", e);
			}
		}
	}

	public void onServiceAdd(CamelContext context, Service service) {
		if (service instanceof ServiceSupport) {
			try {
				ManagedService ms = new ManagedService((ServiceSupport)service);
				agent.register(ms, getNamingStrategy().getObjectName(context, ms));
			}
			catch(JMException e) {
				LOG.warn("Could not register Service MBean", e);
			}
		}
	}

	public void beforeStartRouteType(CamelContext context, RouteType routeType) {
		PerformanceCounter mc = new PerformanceCounter();
		routeType.intercept(new InstrumentationProcessor(mc));

		/*
		 *  Merge performance counter with the MBean it represents instead 
		 *  of registering a new MBean
		try {
			agent.register(mc, getNamingStrategy().getObjectName(context, mc));
		}
		catch(JMException e) {
			LOG.warn("Could not register Counter MBean", e);
		}
		*/
	}

    public CamelNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(CamelNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }
}
