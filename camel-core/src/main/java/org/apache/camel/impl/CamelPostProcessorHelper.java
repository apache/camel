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
package org.apache.camel.impl;

import java.lang.reflect.Method;
import java.util.Set;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consume;
import org.apache.camel.Consumer;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.IsSingleton;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ProxyInstantiationException;
import org.apache.camel.Service;
import org.apache.camel.builder.DefaultFluentProducerTemplate;
import org.apache.camel.component.bean.ProxyHelper;
import org.apache.camel.processor.DeferServiceFactory;
import org.apache.camel.processor.UnitOfWorkProducer;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class for Camel based injector or post processing hooks which can be
 * reused by both the <a href="http://camel.apache.org/spring.html">Spring</a>,
 * <a href="http://camel.apache.org/guice.html">Guice</a> and
 * <a href="http://camel.apache.org/blueprint.html">Blueprint</a> support.
 *
 * @version
 */
public class CamelPostProcessorHelper implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(CamelPostProcessorHelper.class);

    @XmlTransient
    private CamelContext camelContext;

    public CamelPostProcessorHelper() {
    }

    public CamelPostProcessorHelper(CamelContext camelContext) {
        this.setCamelContext(camelContext);
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Does the given context match this camel context
     */
    public boolean matchContext(String context) {
        if (ObjectHelper.isNotEmpty(context)) {
            if (!getCamelContext().getName().equals(context)) {
                return false;
            }
        }
        return true;
    }

    public void consumerInjection(Method method, Object bean, String beanName) {
        Consume consume = method.getAnnotation(Consume.class);
        if (consume != null && matchContext(consume.context())) {
            LOG.debug("Creating a consumer for: {}", consume);
            subscribeMethod(method, bean, beanName, consume.uri(), consume.ref(), consume.property(), consume.predicate());
        }
    }

    public void subscribeMethod(Method method, Object bean, String beanName, String endpointUri, String endpointName, String endpointProperty, String predicate) {
        // lets bind this method to a listener
        String injectionPointName = method.getName();
        Endpoint endpoint = getEndpointInjection(bean, endpointUri, endpointName, endpointProperty, injectionPointName, true);
        if (endpoint != null) {
            boolean multipleConsumer = false;
            if (endpoint instanceof MultipleConsumersSupport) {
                multipleConsumer = ((MultipleConsumersSupport) endpoint).isMultipleConsumersSupported();
            }
            try {
                SubscribeMethodProcessor processor = getConsumerProcessor(endpoint);
                // if multiple consumer then create a new consumer per subscribed method
                if (multipleConsumer || processor == null) {
                    // create new processor and new consumer which happens the first time
                    processor = new SubscribeMethodProcessor(endpoint);
                    // make sure processor is registered in registry so we can reuse it (eg we can look it up)
                    endpoint.getCamelContext().addService(processor, true);
                    processor.addMethod(bean, method, endpoint, predicate);
                    Consumer consumer = endpoint.createConsumer(processor);
                    startService(consumer, endpoint.getCamelContext(), bean, beanName);
                } else {
                    // add method to existing processor
                    processor.addMethod(bean, method, endpoint, predicate);
                }
                if (predicate != null) {
                    LOG.debug("Subscribed method: {} to consume from endpoint: {} with predicate: {}", method, endpoint, predicate);
                } else {
                    LOG.debug("Subscribed method: {} to consume from endpoint: {}", method, endpoint);
                }
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    /**
     * Stats the given service
     */
    protected void startService(Service service, CamelContext camelContext, Object bean, String beanName) throws Exception {
        // defer starting the service until CamelContext has started all its initial services
        if (camelContext != null) {
            camelContext.deferStartService(service, true);
        } else {
            // mo CamelContext then start service manually
            ServiceHelper.startService(service);
        }

        boolean singleton = isSingleton(bean, beanName);
        if (!singleton) {
            LOG.debug("Service is not singleton so you must remember to stop it manually {}", service);
        }
    }

    protected SubscribeMethodProcessor getConsumerProcessor(Endpoint endpoint) {
        Set<SubscribeMethodProcessor> processors = endpoint.getCamelContext().hasServices(SubscribeMethodProcessor.class);
        return processors.stream().filter(s -> s.getEndpoint() == endpoint).findFirst().orElse(null);
    }

    public Endpoint getEndpointInjection(Object bean, String uri, String name, String propertyName,
            String injectionPointName, boolean mandatory) {
        if (ObjectHelper.isEmpty(uri) && ObjectHelper.isEmpty(name)) {
            // if no uri or ref, then fallback and try the endpoint property
            return doGetEndpointInjection(bean, propertyName, injectionPointName);
        } else {
            return doGetEndpointInjection(uri, name, injectionPointName, mandatory);
        }
    }

    private Endpoint doGetEndpointInjection(String uri, String name, String injectionPointName, boolean mandatory) {
        return CamelContextHelper.getEndpointInjection(getCamelContext(), uri, name, injectionPointName, mandatory);
    }

    /**
     * Gets the injection endpoint from a bean property.
     *
     * @param bean the bean
     * @param propertyName the property name on the bean
     */
    private Endpoint doGetEndpointInjection(Object bean, String propertyName, String injectionPointName) {
        // fall back and use the method name if no explicit property name was given
        if (ObjectHelper.isEmpty(propertyName)) {
            propertyName = injectionPointName;
        }

        // we have a property name, try to lookup a getter method on the bean with that name using this strategy
        // 1. first the getter with the name as given
        // 2. then the getter with Endpoint as postfix
        // 3. then if start with on then try step 1 and 2 again, but omit the on prefix
        try {
            Object value = IntrospectionSupport.getOrElseProperty(bean, propertyName, null);
            if (value == null) {
                // try endpoint as postfix
                value = IntrospectionSupport.getOrElseProperty(bean, propertyName + "Endpoint", null);
            }
            if (value == null && propertyName.startsWith("on")) {
                // retry but without the on as prefix
                propertyName = propertyName.substring(2);
                return doGetEndpointInjection(bean, propertyName, injectionPointName);
            }
            if (value == null) {
                return null;
            } else if (value instanceof Endpoint) {
                return (Endpoint) value;
            } else {
                String uriOrRef = getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, value);
                return getCamelContext().getEndpoint(uriOrRef);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error getting property " + propertyName + " from bean " + bean + " due " + e.getMessage(), e);
        }
    }

    /**
     * Creates the object to be injected for an
     * {@link org.apache.camel.EndpointInject} or
     * {@link org.apache.camel.Produce} injection point
     */
    public Object getInjectionValue(Class<?> type, String endpointUri, String endpointRef, String endpointProperty,
            String injectionPointName, Object bean, String beanName) {
        return getInjectionValue(type, endpointUri, endpointRef, endpointProperty, injectionPointName, bean, beanName, true);
    }
    
    /**
     * Creates the object to be injected for an
     * {@link org.apache.camel.EndpointInject} or
     * {@link org.apache.camel.Produce} injection point
     */
    public Object getInjectionValue(Class<?> type, String endpointUri, String endpointRef, String endpointProperty,
            String injectionPointName, Object bean, String beanName, boolean binding) {
        if (type.isAssignableFrom(ProducerTemplate.class)) {
            return createInjectionProducerTemplate(endpointUri, endpointRef, endpointProperty, injectionPointName, bean);
        } else if (type.isAssignableFrom(FluentProducerTemplate.class)) {
            return createInjectionFluentProducerTemplate(endpointUri, endpointRef, endpointProperty, injectionPointName, bean);
        } else if (type.isAssignableFrom(ConsumerTemplate.class)) {
            return createInjectionConsumerTemplate(endpointUri, endpointRef, endpointProperty, injectionPointName);
        } else {
            Endpoint endpoint = getEndpointInjection(bean, endpointUri, endpointRef, endpointProperty, injectionPointName, true);
            if (endpoint != null) {
                if (type.isInstance(endpoint)) {
                    return endpoint;
                } else if (type.isAssignableFrom(Producer.class)) {
                    return createInjectionProducer(endpoint, bean, beanName);
                } else if (type.isAssignableFrom(PollingConsumer.class)) {
                    return createInjectionPollingConsumer(endpoint, bean, beanName);
                } else if (type.isInterface()) {
                    // lets create a proxy
                    try {
                        return ProxyHelper.createProxy(endpoint, binding, type);
                    } catch (Exception e) {
                        throw createProxyInstantiationRuntimeException(type, endpoint, e);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid type: " + type.getName()
                            + " which cannot be injected via @EndpointInject/@Produce for: " + endpoint);
                }
            }
            return null;
        }
    }

    public Object getInjectionPropertyValue(Class<?> type, String propertyName, String propertyDefaultValue,
            String injectionPointName, Object bean, String beanName) {
        try {
            // enforce a properties component to be created if none existed
            CamelContextHelper.lookupPropertiesComponent(getCamelContext(), true);

            String key;
            String prefix = getCamelContext().getPropertyPrefixToken();
            String suffix = getCamelContext().getPropertySuffixToken();
            if (!propertyName.contains(prefix)) {
                // must enclose the property name with prefix/suffix to have it resolved
                key = prefix + propertyName + suffix;
            } else {
                // key has already prefix/suffix so use it as-is as it may be a compound key
                key = propertyName;
            }
            String value = getCamelContext().resolvePropertyPlaceholders(key);
            if (value != null) {
                return getCamelContext().getTypeConverter().mandatoryConvertTo(type, value);
            } else {
                return null;
            }
        } catch (Exception e) {
            if (ObjectHelper.isNotEmpty(propertyDefaultValue)) {
                try {
                    return getCamelContext().getTypeConverter().mandatoryConvertTo(type, propertyDefaultValue);
                } catch (Exception e2) {
                    throw ObjectHelper.wrapRuntimeCamelException(e2);
                }
            }
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public Object getInjectionBeanValue(Class<?> type, String name) {
        if (ObjectHelper.isEmpty(name)) {
            Set<?> found = getCamelContext().getRegistry().findByType(type);
            if (found == null || found.isEmpty()) {
                throw new NoSuchBeanException(name, type.getName());
            } else if (found.size() > 1) {
                throw new NoSuchBeanException("Found " + found.size() + " beans of type: " + type + ". Only one bean expected.");
            } else {
                // we found only one
                return found.iterator().next();
            }
        } else {
            return CamelContextHelper.mandatoryLookup(getCamelContext(), name, type);
        }
    }

    /**
     * Factory method to create a {@link org.apache.camel.ProducerTemplate} to
     * be injected into a POJO
     */
    protected ProducerTemplate createInjectionProducerTemplate(String endpointUri, String endpointRef, String endpointProperty,
            String injectionPointName, Object bean) {
        // endpoint is optional for this injection point
        Endpoint endpoint = getEndpointInjection(bean, endpointUri, endpointRef, endpointProperty, injectionPointName, false);
        CamelContext context = endpoint != null ? endpoint.getCamelContext() : getCamelContext();
        ProducerTemplate answer = new DefaultProducerTemplate(context, endpoint);
        // start the template so its ready to use
        try {
            // no need to defer the template as it can adjust to the endpoint at runtime
            startService(answer, context, bean, null);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    /**
     * Factory method to create a
     * {@link org.apache.camel.FluentProducerTemplate} to be injected into a
     * POJO
     */
    protected FluentProducerTemplate createInjectionFluentProducerTemplate(String endpointUri, String endpointRef, String endpointProperty,
            String injectionPointName, Object bean) {
        // endpoint is optional for this injection point
        Endpoint endpoint = getEndpointInjection(bean, endpointUri, endpointRef, endpointProperty, injectionPointName, false);
        CamelContext context = endpoint != null ? endpoint.getCamelContext() : getCamelContext();
        FluentProducerTemplate answer = new DefaultFluentProducerTemplate(context);
        answer.setDefaultEndpoint(endpoint);
        // start the template so its ready to use
        try {
            // no need to defer the template as it can adjust to the endpoint at runtime
            startService(answer, context, bean, null);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    /**
     * Factory method to create a {@link org.apache.camel.ConsumerTemplate} to
     * be injected into a POJO
     */
    protected ConsumerTemplate createInjectionConsumerTemplate(String endpointUri, String endpointRef, String endpointProperty,
            String injectionPointName) {
        ConsumerTemplate answer = new DefaultConsumerTemplate(getCamelContext());
        // start the template so its ready to use
        try {
            startService(answer, null, null, null);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    /**
     * Factory method to create a started
     * {@link org.apache.camel.PollingConsumer} to be injected into a POJO
     */
    protected PollingConsumer createInjectionPollingConsumer(Endpoint endpoint, Object bean, String beanName) {
        try {
            PollingConsumer consumer = endpoint.createPollingConsumer();
            startService(consumer, endpoint.getCamelContext(), bean, beanName);
            return consumer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    /**
     * A Factory method to create a started {@link org.apache.camel.Producer} to
     * be injected into a POJO
     */
    protected Producer createInjectionProducer(Endpoint endpoint, Object bean, String beanName) {
        try {
            Producer producer = DeferServiceFactory.createProducer(endpoint);
            return new UnitOfWorkProducer(producer);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    protected RuntimeException createProxyInstantiationRuntimeException(Class<?> type, Endpoint endpoint, Exception e) {
        return new ProxyInstantiationException(type, endpoint, e);
    }

    /**
     * Implementations can override this method to determine if the bean is
     * singleton.
     *
     * @param bean the bean
     * @return <tt>true</tt> if its singleton scoped, for prototype scoped
     * <tt>false</tt> is returned.
     */
    protected boolean isSingleton(Object bean, String beanName) {
        if (bean instanceof IsSingleton) {
            IsSingleton singleton = (IsSingleton) bean;
            return singleton.isSingleton();
        }
        return true;
    }
}
