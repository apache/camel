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

import org.apache.camel.Endpoint;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.event.EventComponent;
import org.apache.camel.component.event.EventEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.spring.spi.ApplicationContextRegistry;
import org.apache.camel.spring.spi.SpringInjector;
import org.apache.camel.spring.spi.SpringManagementMBeanAssembler;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.context.Phased;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.Ordered;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * A Spring aware implementation of {@link org.apache.camel.CamelContext} which
 * will automatically register itself with Springs lifecycle methods plus allows
 * spring to be used to customize a any <a
 * href="http://camel.apache.org/type-converter.html">Type Converters</a>
 * as well as supporting accessing components and beans via the Spring
 * {@link ApplicationContext}
 *
 * @version 
 */
public class SpringCamelContext extends DefaultCamelContext implements Lifecycle, ApplicationContextAware, Phased,
        ApplicationListener<ApplicationEvent>, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(SpringCamelContext.class);
    private static final ThreadLocal<Boolean> NO_START = new ThreadLocal<>();
    private ApplicationContext applicationContext;
    private EventComponent eventComponent;
    private boolean shutdownEager = true;

    public SpringCamelContext() {
    }

    public SpringCamelContext(ApplicationContext applicationContext) {
        setApplicationContext(applicationContext);
    }

    public static void setNoStart(boolean b) {
        if (b) {
            NO_START.set(true);
        } else {
            NO_START.set(null);
        }
    }

    /**
     * @deprecated its better to create and boot Spring the standard Spring way and to get hold of CamelContext
     * using the Spring API.
     */
    @Deprecated
    public static SpringCamelContext springCamelContext(ApplicationContext applicationContext) throws Exception {
        return springCamelContext(applicationContext, true);
    }

    /**
     * @deprecated its better to create and boot Spring the standard Spring way and to get hold of CamelContext
     * using the Spring API.
     */
    @Deprecated
    public static SpringCamelContext springCamelContext(ApplicationContext applicationContext, boolean maybeStart) throws Exception {
        if (applicationContext != null) {
            // lets try and look up a configured camel context in the context
            String[] names = applicationContext.getBeanNamesForType(SpringCamelContext.class);
            if (names.length == 1) {
                return applicationContext.getBean(names[0], SpringCamelContext.class);
            }
        }
        SpringCamelContext answer = new SpringCamelContext();
        answer.setApplicationContext(applicationContext);
        if (maybeStart) {
            answer.start();
        }
        return answer;
    }

    /**
     * @deprecated its better to create and boot Spring the standard Spring way and to get hold of CamelContext
     * using the Spring API.
     */
    @Deprecated
    public static SpringCamelContext springCamelContext(String configLocations) throws Exception {
        return springCamelContext(new ClassPathXmlApplicationContext(configLocations));
    }

    @Override
    public void start() {
        // for example from unit testing we want to start Camel later (manually)
        if (Boolean.TRUE.equals(NO_START.get())) {
            LOG.trace("Ignoring start() as NO_START is false");
            return;
        }

        if (!isStarted() && !isStarting()) {
            try {
                StopWatch watch = new StopWatch();
                super.start();
                LOG.debug("start() took {} millis", watch.stop());
            } catch (Exception e) {
                throw wrapRuntimeCamelException(e);
            }
        } else {
            // ignore as Camel is already started
            LOG.trace("Ignoring start() as Camel is already started");
        }
    }

    @Override
    public void stop() {
        if (!isStopping() && !isStopped()) {
            try {
                super.stop();
            } catch (Exception e) {
                throw wrapRuntimeCamelException(e);
            }
        } else {
            // ignore as Camel is already stopped
            LOG.trace("Ignoring stop() as Camel is already stopped");
        }
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        LOG.debug("onApplicationEvent: {}", event);

        if (event instanceof ContextRefreshedEvent) {
            // nominally we would prefer to use Lifecycle interface that
            // would invoke start() method, but in order to do that 
            // SpringCamelContext needs to implement SmartLifecycle
            // (look at DefaultLifecycleProcessor::startBeans), but it
            // cannot implement it as it already implements
            // RuntimeConfiguration, and both SmartLifecycle and
            // RuntimeConfiguration declare isAutoStartup method but
            // with boolean and Boolean return types, and covariant
            // methods with primitive types are not allowed by the JLS
            // so we need to listen for ContextRefreshedEvent and start
            // on its reception
            start();
        }

        if (eventComponent != null) {
            eventComponent.onApplicationEvent(event);
        }
    }

    @Override
    public int getOrder() {
        // SpringCamelContext implements Ordered so that it's the last
        // in ApplicationListener to receive events, this is important
        // for startup as we want all resources to be ready and all
        // routes added to the context (see
        // org.apache.camel.spring.boot.RoutesCollector)
        // and we need to be after CamelContextFactoryBean
        return LOWEST_PRECEDENCE;
    }

    // Properties
    // -----------------------------------------------------------------------

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        ClassLoader cl;

        // set the application context classloader
        if (applicationContext != null && applicationContext.getClassLoader() != null) {
            cl = applicationContext.getClassLoader();
        } else {
            LOG.warn("Cannot find the class loader from application context, using the thread context class loader instead");
            cl = Thread.currentThread().getContextClassLoader();
        }
        LOG.debug("Set the application context classloader to: {}", cl);
        this.setApplicationContextClassLoader(cl);

        if (applicationContext instanceof ConfigurableApplicationContext) {
            // only add if not already added
            if (hasComponent("spring-event") == null) {
                eventComponent = new EventComponent(applicationContext);
                addComponent("spring-event", eventComponent);
            }
        }
    }

    @Deprecated
    public EventEndpoint getEventEndpoint() {
        return null;
    }

    @Deprecated
    public void setEventEndpoint(EventEndpoint eventEndpoint) {
        // noop
    }

    /**
     * Whether to shutdown this {@link org.apache.camel.spring.SpringCamelContext} eager (first)
     * when Spring {@link org.springframework.context.ApplicationContext} is being stopped.
     * <p/>
     * <b>Important:</b> This option is default <tt>true</tt> which ensures we shutdown Camel
     * before other beans. Setting this to <tt>false</tt> restores old behavior in earlier
     * Camel releases, which can be used for special cases to behave as before.
     *
     * @return <tt>true</tt> to shutdown eager (first), <tt>false</tt> to shutdown last
     */
    public boolean isShutdownEager() {
        return shutdownEager;
    }

    /**
     * @see #isShutdownEager()
     */
    public void setShutdownEager(boolean shutdownEager) {
        this.shutdownEager = shutdownEager;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    @Override
    protected Injector createInjector() {
        if (applicationContext instanceof ConfigurableApplicationContext) {
            return new SpringInjector((ConfigurableApplicationContext)applicationContext);
        } else {
            LOG.warn("Cannot use SpringInjector as applicationContext is not a ConfigurableApplicationContext as its: "
                      + applicationContext);
            return super.createInjector();
        }
    }

    @Override
    protected ManagementMBeanAssembler createManagementMBeanAssembler() {
        // use a spring mbean assembler
        return new SpringManagementMBeanAssembler(this);
    }

    protected EventEndpoint createEventEndpoint() {
        return getEndpoint("spring-event:default", EventEndpoint.class);
    }

    @Override
    protected Endpoint convertBeanToEndpoint(String uri, Object bean) {
        // We will use the type convert to build the endpoint first
        Endpoint endpoint = getTypeConverter().convertTo(Endpoint.class, bean);
        if (endpoint != null) {
            endpoint.setCamelContext(this);
            return endpoint;
        }

        return new ProcessorEndpoint(uri, this, new BeanProcessor(bean, this));
    }

    @Override
    protected Registry createRegistry() {
        return new ApplicationContextRegistry(getApplicationContext());
    }

    @Override
    protected ModelJAXBContextFactory createModelJAXBContextFactory() {
        return new SpringModelJAXBContextFactory();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SpringCamelContext(").append(getName()).append(")");
        if (applicationContext != null) {
            sb.append(" with spring id ").append(applicationContext.getId());
        }
        return sb.toString();
    }

    @Override
    public int getPhase() {
        // the context is started by invoking start method which
        // happens either on ContextRefreshedEvent or explicitly
        // invoking the method, for instance CamelContextFactoryBean
        // is using that to start the context, _so_ here we want to
        // have maximum priority as the getPhase() will be used only
        // for stopping, in order to be used for starting we would
        // need to implement SmartLifecycle which we cannot
        // (explained in comment in the onApplicationEvent method)
        // we use LOWEST_PRECEDENCE here as this is taken into account
        // only when stopping and then in reversed order
        return LOWEST_PRECEDENCE;
    }

    @Override
    public boolean isRunning() {
        return !isStopping() && !isStopped();
    }

}
