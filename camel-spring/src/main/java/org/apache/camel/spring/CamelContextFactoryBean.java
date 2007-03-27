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
package org.apache.camel.spring;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.List;

/**
 * A Spring {@link FactoryBean} to create and initialize a Camel {@CamelContext}
 * and install routes either explicitly configured in Spring or found by searching the classpath.
 *
 * @version $Revision$
 */
public class CamelContextFactoryBean implements FactoryBean, InitializingBean, DisposableBean, ApplicationContextAware {
    private CamelContext context;
    private boolean singleton = true;
    private RouteBuilder routeBuilder;
    private List<RouteBuilder> additionalBuilders = new ArrayList<RouteBuilder>();
    private String[] packages = {};
    private ApplicationContext applicationContext;

    public Object getObject() throws Exception {
        return getContext();
    }

    public Class getObjectType() {
        return CamelContext.class;
    }

    public boolean isSingleton() {
        return singleton;
    }

    public void afterPropertiesSet() throws Exception {

        // lets force any lazy creation
        getContext();

        findRouteBuiders();
        installRoutes();

        // now lets activate the routes
        getContext().activateEndpoints();
    }

    public void destroy() throws Exception {
        getContext().deactivateEndpoints();
    }

    // Properties
    //-------------------------------------------------------------------------
    public CamelContext getContext() {
        if (context == null) {
            context = new DefaultCamelContext();
        }
        return context;
    }

    public void setContext(CamelContext context) {
        this.context = context;
    }

	public RouteBuilder getRouteBuilder() {
		return routeBuilder;
	}

	public void setRouteBuilder(RouteBuilder routeBuilder) {
		this.routeBuilder = routeBuilder;
	}

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String[] packages) {
        this.packages = packages;
    }


    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * Strategy to install all available routes into the context
     */
    protected void installRoutes() throws Exception {
        for (RouteBuilder routeBuilder : additionalBuilders) {
            getContext().addRoutes(routeBuilder);
        }
        if( routeBuilder!=null )
        	getContext().addRoutes(routeBuilder);
    }

    /**
     * Strategy method to try find {@link RouteBuilder} instances on the classpath
     */
    protected void findRouteBuiders() throws IllegalAccessException, InstantiationException {
        if (packages != null && packages.length > 0) {
            RouteBuilderFinder finder = new RouteBuilderFinder(this);
            finder.appendBuilders(additionalBuilders);
        }
    }
}
