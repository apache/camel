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
import java.util.Map;

import javax.management.MBeanServer;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.RouteBuilderRef;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.dataformat.DataFormatType;
import org.apache.camel.processor.interceptor.Debugger;
import org.apache.camel.spi.InstrumentationAgent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
    @XmlAttribute(required = false)
    private Boolean useJmx;
    @XmlAttribute(required = false)
    private String mbeanServer;
    @XmlAttribute(required = false)
    private Boolean autowireRouteBuilders = Boolean.TRUE;
    @XmlElement(name = "package", required = false)
    private String[] packages = {};
    @XmlElement(name = "jmxAgent", type = CamelJMXAgentType.class, required = false)
    private CamelJMXAgentType camelJMXAgent;
    @XmlElements({
        @XmlElement(name = "beanPostProcessor", type = CamelBeanPostProcessor.class, required = false),
        @XmlElement(name = "template", type = CamelTemplateFactoryBean.class, required = false),
        @XmlElement(name = "proxy", type = CamelProxyFactoryType.class, required = false),
        @XmlElement(name = "export", type = CamelServiceExporterType.class, required = false)})
    private List beans;
    @XmlElement(name = "routeBuilderRef", required = false)
    private List<RouteBuilderRef> builderRefs = new ArrayList<RouteBuilderRef>();
    @XmlElement(name = "endpoint", required = false)
    private List<EndpointFactoryBean> endpoints;
    @XmlElementRef
    private List<DataFormatType> dataFormats;
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
    @XmlTransient
    private ClassLoader contextClassLoaderOnStart;
    @XmlTransient
    private InstrumentationAgent instrumentationAgent;
    @XmlTransient
    private BeanPostProcessor beanPostProcessor;


    public CamelContextFactoryBean() {

        // Lets keep track of the class loader for when we actually do start things up
        contextClassLoaderOnStart = Thread.currentThread().getContextClassLoader();
    }

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
        // lets see if we can find a debugger to add
        // TODO there should be a neater way to do this!
        Debugger debugger = null;
        String[] names = getApplicationContext().getBeanNamesForType(Debugger.class, true, true);
        if (names.length == 1) {
            debugger = (Debugger) getApplicationContext().getBean(names[0], Debugger.class);
        }
        if (debugger == null) {
            ApplicationContext parentContext = getApplicationContext().getParent();
            if (parentContext != null) {
                names = parentContext.getBeanNamesForType(Debugger.class, true, true);
                if (names.length == 1) {
                    debugger = (Debugger) parentContext.getBean(names[0], Debugger.class);
                }
            }
        }
        if (debugger != null) {
            getContext().addInterceptStrategy(debugger);
        }

        // Set the application context and camelContext for the beanPostProcessor
        if (beanPostProcessor != null) {
            if (beanPostProcessor instanceof ApplicationContextAware) {
                ((ApplicationContextAware)beanPostProcessor).setApplicationContext(applicationContext);
            }
            if (beanPostProcessor instanceof CamelBeanPostProcessor) {
                ((CamelBeanPostProcessor)beanPostProcessor).setCamelContext(getContext());
            }

        }
        // lets force any lazy creation
        getContext().addRouteDefinitions(routes);
        // set the instrumentation agent here
        if (instrumentationAgent == null && isJmxEnabled()) {
            SpringInstrumentationAgent agent = new SpringInstrumentationAgent();
            if (camelJMXAgent != null) {
                agent.enableJmx(camelJMXAgent.getJmxDomainName(), camelJMXAgent.getConnectorPath(), camelJMXAgent.getConnectorPort());
                if (camelJMXAgent.isCreateConnector() != null) {
                    agent.setCreateConnector(camelJMXAgent.isCreateConnector());
                }
                if (camelJMXAgent.isUsePlatformMBeanServer() != null) {
                    agent.setUsePlatformMBeanServer(camelJMXAgent.isUsePlatformMBeanServer());
                }
            } else {
                agent.enableJmx();
            }
            agent.setCamelContext(getContext());
            String name = getMbeanServer();
            if (name != null) {
                MBeanServer mbeanServer = (MBeanServer) getApplicationContext().getBean(name, MBeanServer.class);
                agent.setMBeanServer(mbeanServer);
            }
            instrumentationAgent = agent;
            instrumentationAgent.start();
        }

        LOG.debug("Found JAXB created routes: " + getRoutes());

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
            context = createContext();
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
        if (applicationContext == null) {
            throw new IllegalArgumentException("No applicationContext has been injected!");
        }
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

    public String getMbeanServer() {
        return mbeanServer;
    }

    public void setMbeanServer(String mbeanServer) {
        this.mbeanServer = mbeanServer;
    }

    public void setBeanPostProcessor(BeanPostProcessor postProcessor) {
        this.beanPostProcessor = postProcessor;
    }

    public BeanPostProcessor getBeanPostProcessor() {
        return beanPostProcessor;
    }

    public boolean isJmxEnabled() {
        return useJmx != null && useJmx.booleanValue();
    }

    public Boolean getUseJmx() {
        return useJmx;
    }

    public void setUseJmx(Boolean useJmx) {
        this.useJmx = useJmx;
    }

    public void setCamelJMXAgent(CamelJMXAgentType agent) {
        camelJMXAgent = agent;
    }

    public CamelJMXAgentType getCamelJMXAgent() {
        return camelJMXAgent;
    }

    public List<RouteBuilderRef> getBuilderRefs() {
        return builderRefs;
    }

    public void setBuilderRefs(List<RouteBuilderRef> builderRefs) {
        this.builderRefs = builderRefs;
    }

    /**
     * If enabled this will force all {@link RouteBuilder} classes configured in the Spring
     * {@link ApplicationContext} to be registered automatically with this CamelContext.
     */
    public void setAutowireRouteBuilders(Boolean autowireRouteBuilders) {
        this.autowireRouteBuilders = autowireRouteBuilders;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Create the context
     */
    protected SpringCamelContext createContext() {
        SpringCamelContext ctx = new SpringCamelContext(getApplicationContext());
        ctx.setName(getId());
        return ctx;
    }

    /**
     * Strategy to install all available routes into the context
     */
    protected void installRoutes() throws Exception {
        if (autowireRouteBuilders != null && autowireRouteBuilders.booleanValue()) {
            Map builders = getApplicationContext().getBeansOfType(RouteBuilder.class, true, true);
            if (builders != null) {
                for (Object builder : builders.values()) {
                    getContext().addRoutes((RouteBuilder) builder);
                }
            }
        }
        for (RouteBuilder routeBuilder : additionalBuilders) {
            getContext().addRoutes(routeBuilder);
        }
        if (routeBuilder != null) {
            getContext().addRoutes(routeBuilder);
        }

        // lets add route builders added from references
        if (builderRefs != null) {
            for (RouteBuilderRef builderRef : builderRefs) {
                RouteBuilder builder = builderRef.createRouteBuilder(getContext());
                getContext().addRoutes(builder);
            }
        }
    }

    /**
     * Strategy method to try find {@link RouteBuilder} instances on the
     * classpath
     */
    protected void findRouteBuiders() throws Exception, InstantiationException {
        if (packages != null && packages.length > 0) {
            RouteBuilderFinder finder = new RouteBuilderFinder(getContext(), packages, contextClassLoaderOnStart, getBeanPostProcessor());
            finder.appendBuilders(additionalBuilders);
        }
    }
}
