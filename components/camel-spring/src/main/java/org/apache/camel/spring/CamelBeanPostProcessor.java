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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.Service;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spring.util.ReflectionUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * A bean post processor which implements the <a href="http://camel.apache.org/bean-integration.html">Bean Integration</a>
 * features in Camel. Features such as the <a href="http://camel.apache.org/bean-injection.html">Bean Injection</a> of objects like
 * {@link Endpoint} and
 * {@link org.apache.camel.ProducerTemplate} together with support for
 * <a href="http://camel.apache.org/pojo-consuming.html">POJO Consuming</a> via the
 * {@link org.apache.camel.Consume} annotation along with
 * <a href="http://camel.apache.org/pojo-producing.html">POJO Producing</a> via the
 * {@link org.apache.camel.Produce} annotation along with other annotations such as
 * {@link org.apache.camel.RecipientList} for creating <a href="http://camel.apache.org/recipientlist-annotation.html">a Recipient List router via annotations</a>.
 * <p>
 * If you use the &lt;camelContext&gt; element in your <a href="http://camel.apache.org/spring.html">Spring XML</a>
 * then one of these bean post processors is implicitly installed and configured for you. So you should never have to
 * explicitly create or configure one of these instances.
 *
 * @version 
 */
@XmlRootElement(name = "beanPostProcessor")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
    private static final transient Logger LOG = LoggerFactory.getLogger(CamelBeanPostProcessor.class);
    @XmlTransient
    Set<String> prototypeBeans = new LinkedHashSet<String>();
    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private ApplicationContext applicationContext;
    @XmlTransient
    private CamelPostProcessorHelper postProcessor;
    @XmlTransient
    private String camelId;

    public CamelBeanPostProcessor() {
    }

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        LOG.trace("Camel bean processing before initialization for bean: {}", beanName);

        // some beans cannot be post processed at this given time, so we gotta check beforehand
        if (!canPostProcessBean(bean, beanName)) {
            return bean;
        }

        injectFields(bean, beanName);
        injectMethods(bean, beanName);

        if (bean instanceof CamelContextAware && canSetCamelContext(bean, beanName)) {
            CamelContextAware contextAware = (CamelContextAware)bean;
            CamelContext context = getOrLookupCamelContext();
            if (context == null) {
                LOG.warn("No CamelContext defined yet so cannot inject into bean: " + beanName);
            } else {
                contextAware.setCamelContext(context);
            }
        }

        return bean;
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        LOG.trace("Camel bean processing after initialization for bean: {}", beanName);

        // some beans cannot be post processed at this given time, so we gotta check beforehand
        if (!canPostProcessBean(bean, beanName)) {
            return bean;
        }

        if (bean instanceof DefaultEndpoint) {
            DefaultEndpoint defaultEndpoint = (DefaultEndpoint) bean;
            defaultEndpoint.setEndpointUriIfNotSpecified(beanName);
        }

        return bean;
    }

    // Properties
    // -------------------------------------------------------------------------

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getCamelId() {
        return camelId;
    }

    public void setCamelId(String camelId) {
        this.camelId = camelId;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Can we post process the given bean?
     *
     * @param bean the bean
     * @param beanName the bean name
     * @return true to process it
     */
    protected boolean canPostProcessBean(Object bean, String beanName) {
        // the JMXAgent is a bit strange and causes Spring issues if we let it being
        // post processed by this one. It does not need it anyway so we are good to go.
        if (bean instanceof CamelJMXAgentDefinition) {
            return false;
        }

        // all other beans can of course be processed
        return true;
    }
    
    
    protected boolean canSetCamelContext(Object bean, String beanName) {
        if (bean instanceof CamelContextAware) {
            CamelContextAware camelContextAware = (CamelContextAware) bean;
            CamelContext context = camelContextAware.getCamelContext();
            if (context != null) {
                LOG.trace("CamelContext already set on bean with id [{}]. Will keep existing CamelContext on bean.", beanName);
                return false;
            }
        }

        return true;
    }

    /**
     * A strategy method to allow implementations to perform some custom JBI
     * based injection of the POJO
     *
     * @param bean the bean to be injected
     */
    protected void injectFields(final Object bean, final String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), new ReflectionUtils.FieldCallback() {
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
                if (endpointInject != null && getPostProcessor().matchContext(endpointInject.context())) {
                    injectField(field, endpointInject.uri(), endpointInject.ref(), bean, beanName);
                }

                Produce produce = field.getAnnotation(Produce.class);
                if (produce != null && getPostProcessor().matchContext(produce.context())) {
                    injectField(field, produce.uri(), produce.ref(), bean, beanName);
                }
            }
        });
    }

    protected void injectField(Field field, String endpointUri, String endpointRef, Object bean, String beanName) {
        ReflectionUtils.setField(field, bean, getPostProcessor().getInjectionValue(field.getType(), endpointUri, endpointRef, field.getName(), bean, beanName));
    }

    protected void injectMethods(final Object bean, final String beanName) {
        ReflectionUtils.doWithMethods(bean.getClass(), new ReflectionUtils.MethodCallback() {           
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                setterInjection(method, bean, beanName);
                getPostProcessor().consumerInjection(method, bean, beanName);
            }
        });
    }

    protected void setterInjection(Method method, Object bean, String beanName) {
        EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
        if (endpointInject != null && getPostProcessor().matchContext(endpointInject.context())) {
            setterInjection(method, bean, beanName, endpointInject.uri(), endpointInject.ref());
        }

        Produce produce = method.getAnnotation(Produce.class);
        if (produce != null && getPostProcessor().matchContext(produce.context())) {
            setterInjection(method, bean, beanName, produce.uri(), produce.ref());
        }
    }

    protected void setterInjection(Method method, Object bean, String beanName, String endpointUri, String endpointRef) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes != null) {
            if (parameterTypes.length != 1) {
                LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: " + method);
            } else {
                String propertyName = ObjectHelper.getPropertyName(method);
                Object value = getPostProcessor().getInjectionValue(parameterTypes[0], endpointUri, endpointRef, propertyName, bean, beanName);
                ObjectHelper.invokeMethod(method, bean, value);
            }
        }
    }

    protected CamelContext getOrLookupCamelContext() {
        if (camelContext == null && applicationContext.containsBean(camelId)) {
            camelContext = (CamelContext) applicationContext.getBean(camelId);
        }
        return camelContext;
    }

    public CamelPostProcessorHelper getPostProcessor() {
        // lets lazily create the post processor
        if (postProcessor == null) {
            postProcessor = new CamelPostProcessorHelper() {

                @Override
                public CamelContext getCamelContext() {
                    // lets lazily lookup the camel context here
                    // as doing this will cause this context to be started immediately
                    // breaking the lifecycle ordering of different camel contexts
                    // so we only want to do this on demand
                    return getOrLookupCamelContext();
                }

                @Override
                protected RuntimeException createProxyInstantiationRuntimeException(Class<?> type, Endpoint endpoint, Exception e) {
                    return new BeanInstantiationException(type, "Could not instantiate proxy of type " + type.getName() + " on endpoint " + endpoint, e);
                }

                protected boolean isSingleton(Object bean, String beanName) {
                    // no application context has been injected which means the bean
                    // has not been enlisted in Spring application context
                    if (applicationContext == null || beanName == null) {
                        return super.isSingleton(bean, beanName);
                    } else {
                        return applicationContext.isSingleton(beanName);
                    }
                }

                protected void startService(Service service, Object bean, String beanName) throws Exception {
                    if (isSingleton(bean, beanName)) {
                        getCamelContext().addService(service);
                    } else {
                        // only start service and do not add it to CamelContext
                        ServiceHelper.startService(service);
                        if (prototypeBeans.add(beanName)) {
                            // do not spam the log with WARN so do this only once per bean name
                            LOG.warn("The bean with id [" + beanName + "] is prototype scoped and cannot stop the injected service when bean is destroyed: "
                                    + service + ". You may want to stop the service manually from the bean.");
                        }
                    }
                }
            };
        }
        return postProcessor;
    }

}
