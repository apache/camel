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
package org.apache.camel.spring;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.model.RouteType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * A Spring {@link FactoryBean} to create and initialize a
 * {@link SpringCamelContext} and install routes either explicitly configured in
 * Spring XML or found by searching the classpath for Java classes which extend
 * {@link RouteBuilder} using the nested {@link #setPackages(String[])}.
 * 
 * @version $Revision$
 */
@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends IdentifiedType implements RouteContainer, FactoryBean, InitializingBean, DisposableBean, ApplicationContextAware, ApplicationListener {
    private static final Log LOG = LogFactory.getLog(CamelContextFactoryBean.class);
    @XmlElement(name = "package", required = false)
    private String[] packages = {};
    @XmlElements({@XmlElement(name = "beanPostProcessor", type = CamelBeanPostProcessor.class, required = false), @XmlElement(name = "proxy", type = CamelProxyFactoryType.class, required = false),
                   @XmlElement(name = "export", type = CamelServiceExporterType.class, required = false) })
    private List beans;
    @XmlElement(name = "endpoint", required = false)
    private List<EndpointFactoryBean> endpoints;
    @XmlElement(name = "route", required = false)
    private List<RouteType> routes = new ArrayList<RouteType>();
    @XmlTransient
    private SpringCamelContext context;
    @XmlTransient
    private RouteBuilder routeBuilder;
    @XmlTransient
    private List<RouteBuilder> additionalBuilders = new ArrayList<RouteBuilder>();
    @XmlTransient
    private ApplicationContext applicationContext;

    public Object getObject() throws Exception {
        return getContext();
    }

    public Class getObjectType() {
        return SpringCamelContext.class;
    }

    public boolean isSingleton() {
        return true;
    }

    public void afterPropertiesSet() throws Exception {

        // lets force any lazy creation
        getContext();

        LOG.debug("Found JAXB created routes: " + getRoutes());
        String[] names = applicationContext.getBeanNamesForType(SpringInstrumentationAgent.class);
        if (names.length == 1) {
            applicationContext.getBean(names[0], SpringInstrumentationAgent.class);
        }
        
        findRouteBuiders();
        installRoutes();
    }

    public void destroy() throws Exception {
        getContext().stop();
    }

    public void onApplicationEvent(ApplicationEvent event) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Publishing event: " + event);
        }

        if (event instanceof ContextRefreshedEvent) {
            // now lets start the CamelContext so that all its possible
            // dependencies are initailized
            try {
                LOG.debug("Starting the context now!");
                getContext().start();
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
        /*
         * if (context != null) { context.onApplicationEvent(event); }
         */
    }

    // Properties
    // -------------------------------------------------------------------------
    public SpringCamelContext getContext() throws Exception {
        if (context == null) {
            context = new SpringCamelContext(getApplicationContext());
        }
        return context;
    }

    public void setContext(SpringCamelContext context) {
        this.context = context;
    }

    public List<RouteType> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteType> routes) {
        this.routes = routes;
    }

    public RouteBuilder getRouteBuilder() {
        return routeBuilder;
    }

    /**
     * Set a single {@link RouteBuilder} to be used to create the default routes
     * on startup
     */
    public void setRouteBuilder(RouteBuilder routeBuilder) {
        this.routeBuilder = routeBuilder;
    }

    /**
     * Set a collection of {@link RouteBuilder} instances to be used to create
     * the default routes on startup
     */
    public void setRouteBuilders(RouteBuilder[] builders) {
        for (RouteBuilder builder : builders) {
            additionalBuilders.add(builder);
        }
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

    /**
     * Sets the package names to be recursively searched for Java classes which
     * extend {@link RouteBuilder} to be auto-wired up to the
     * {@link SpringCamelContext} as a route. Note that classes are excluded if
     * they are specifically configured in the spring.xml
     * 
     * @param packages the package names which are recursively searched
     */
    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Strategy to install all available routes into the context
     */
    protected void installRoutes() throws Exception {
        for (RouteBuilder routeBuilder : additionalBuilders) {
            getContext().addRoutes(routeBuilder);
        }
        if (routeBuilder != null) {
            getContext().addRoutes(routeBuilder);
        }
        for (RouteType route : routes) {
            route.addRoutes(getContext());
        }
    }

    /**
     * Strategy method to try find {@link RouteBuilder} instances on the
     * classpath
     */
    protected void findRouteBuiders() throws Exception, InstantiationException {
        if (packages != null && packages.length > 0) {
            RouteBuilderFinder finder = new RouteBuilderFinder(getContext(), packages);
            finder.appendBuilders(additionalBuilders);
        }
    }

}
