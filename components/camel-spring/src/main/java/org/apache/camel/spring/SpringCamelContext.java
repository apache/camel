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
import org.apache.camel.spi.Registry;
import org.apache.camel.spring.spi.ApplicationContextRegistry;
import org.apache.camel.spring.spi.SpringInjector;
import org.apache.camel.spring.spi.SpringManagementMBeanAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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
public class SpringCamelContext extends DefaultCamelContext implements InitializingBean, DisposableBean,
        ApplicationContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(SpringCamelContext.class);
    private static final ThreadLocal<Boolean> NO_START = new ThreadLocal<Boolean>();
    private ApplicationContext applicationContext;
    private EventComponent eventComponent;
    private boolean shutdownEager = true;

    public SpringCamelContext() {
    }

    public SpringCamelContext(ApplicationContext applicationContext) {
        setApplicationContext(applicationContext);
        setModelJAXBContextFactory(new SpringModelJAXBContextFactory());
    }

    public static void setNoStart(boolean b) {
        if (b) {
            NO_START.set(b);
        } else {
            NO_START.remove();
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
            answer.afterPropertiesSet();
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

    public void afterPropertiesSet() throws Exception {
        maybeStart();
    }

    public void destroy() throws Exception {
        stop();
    }

    public void onApplicationEvent(ApplicationEvent event) {
        LOG.debug("onApplicationEvent: {}", event);

        if (event instanceof ContextRefreshedEvent) {
            // now lets start the CamelContext so that all its possible
            // dependencies are initialized
            try {
                maybeStart();
            } catch (Exception e) {
                throw wrapRuntimeCamelException(e);
            }
        } else if (event instanceof ContextClosedEvent) {
            // ContextClosedEvent is emitted when Spring is about to be shutdown
            if (isShutdownEager()) {
                try {
                    maybeStop();
                } catch (Exception e) {
                    throw wrapRuntimeCamelException(e);
                }
            }
        } else if (event instanceof ContextStoppedEvent) {
            // ContextStoppedEvent is emitted when Spring is end of shutdown
            try {
                maybeStop();
            } catch (Exception e) {
                throw wrapRuntimeCamelException(e);
            }
        }

        if (eventComponent != null) {
            eventComponent.onApplicationEvent(event);
        }
    }

    // Properties
    // -----------------------------------------------------------------------

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

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

    private void maybeStart() throws Exception {
        // for example from unit testing we want to start Camel later and not when Spring framework
        // publish a ContextRefreshedEvent

        if (NO_START.get() == null) {
            if (!isStarted() && !isStarting()) {
                start();
            } else {
                // ignore as Camel is already started
                LOG.trace("Ignoring maybeStart() as Apache Camel is already started");
            }
        } else {
            LOG.trace("Ignoring maybeStart() as NO_START is false");
        }
    }

    private void maybeStop() throws Exception {
        if (!isStopping() && !isStopped()) {
            stop();
        } else {
            // ignore as Camel is already stopped
            LOG.trace("Ignoring maybeStop() as Apache Camel is already stopped");
        }
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

}
