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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Consume;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.MessageDriven;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.bean.ProxyHelper;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.spring.util.ReflectionUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import static org.apache.camel.util.ObjectHelper.isNotNullAndNonEmpty;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;
import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * A bean post processor which implements the <a href="http://activemq.apache.org/camel/bean-integration.html">Bean Integration</a>
 * features in Camel such as the <a href="http://activemq.apache.org/camel/bean-injection.html">Bean Injection</a> of objects like
 * {@link Endpoint} and
 * {@link org.apache.camel.ProducerTemplate} together with support for
 * <a href="http://activemq.apache.org/camel/pojo-consuming.html">POJO Consuming</a> via the 
 * {@link org.apache.camel.Consume} and {@link org.apache.camel.MessageDriven} annotations along with
 * <a href="http://activemq.apache.org/camel/pojo-producing.html">POJO Producing</a> via the
 * {@link org.apache.camel.Produce} annotation along with other annotations such as
 * {@link org.apache.camel.RecipientList} for creating <a href="http://activemq.apache.org/camel/recipientlist-annotation.html">a Recipient List router via annotations</a>.
 * <p>
 * If you use the &lt;camelContext&gt; element in your <a href="http://activemq.apache.org/camel/spring.html">Spring XML</a> 
 * then one of these bean post processors is implicity installed and configured for you. So you should never have to
 * explicitly create or configure one of these instances.
 *
 * @version $Revision$
 */
@XmlRootElement(name = "beanPostProcessor")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
    private static final transient Log LOG = LogFactory.getLog(CamelBeanPostProcessor.class);
    @XmlTransient
    private SpringCamelContext camelContext;
    @XmlTransient
    private ApplicationContext applicationContext;

    public CamelBeanPostProcessor() {
    }

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        injectFields(bean);
        injectMethods(bean);
        if (bean instanceof CamelContextAware) {
            CamelContextAware contextAware = (CamelContextAware)bean;
            if (camelContext == null) {
                LOG.warn("No CamelContext defined yet so cannot inject into: " + bean);
            } else {
                contextAware.setCamelContext(camelContext);
            }
        }
        return bean;
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    // Properties
    // -------------------------------------------------------------------------

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public SpringCamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(SpringCamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * A strategy method to allow implementations to perform some custom JBI
     * based injection of the POJO
     *
     * @param bean the bean to be injected
     */
    protected void injectFields(final Object bean) {
        ReflectionUtils.doWithFields(bean.getClass(), new ReflectionUtils.FieldCallback() {
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                EndpointInject annotation = field.getAnnotation(EndpointInject.class);
                if (annotation != null) {
                    injectField(field, annotation.uri(), annotation.name(), bean);
                }
                Produce produce = field.getAnnotation(Produce.class);
                if (produce != null) {
                    injectField(field, produce.uri(), produce.ref(), bean);
                }
            }
        });
    }

    protected void injectField(Field field, String endpointUri, String endpointRef, Object bean) {
        ReflectionUtils.setField(field, bean, getInjectionValue(field.getType(), endpointUri, endpointRef, field.getName()));
    }

    protected void injectMethods(final Object bean) {
        ReflectionUtils.doWithMethods(bean.getClass(), new ReflectionUtils.MethodCallback() {
            @SuppressWarnings("unchecked")
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                setterInjection(method, bean);
                consumerInjection(method, bean);
            }
        });
    }

    protected void setterInjection(Method method, Object bean) {
        EndpointInject annoation = method.getAnnotation(EndpointInject.class);
        if (annoation != null) {
            setterInjection(method, bean, annoation.uri(), annoation.name());
        }
        Produce produce = method.getAnnotation(Produce.class);
        if (produce != null) {
            setterInjection(method, bean, produce.uri(), produce.ref());
        }
    }

    protected void setterInjection(Method method, Object bean, String endpointUri, String endpointRef) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes != null) {
            if (parameterTypes.length != 1) {
                LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: " + method);
            } else {
                String propertyName = ObjectHelper.getPropertyName(method);
                Object value = getInjectionValue(parameterTypes[0], endpointUri, endpointRef, propertyName);
                ObjectHelper.invokeMethod(method, bean, value);
            }
        }
    }

    protected void consumerInjection(final Object bean) {
        ReflectionUtils.doWithMethods(bean.getClass(), new ReflectionUtils.MethodCallback() {
            @SuppressWarnings("unchecked")
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                /*
                 * TODO support callbacks? if
                 * (method.getAnnotation(Callback.class) != null) { try {
                 * Expression e = ExpressionFactory.createExpression(
                 * method.getAnnotation(Callback.class).condition());
                 * JexlContext jc = JexlHelper.createContext();
                 * jc.getVars().put("this", obj); Object r = e.evaluate(jc); if
                 * (!(r instanceof Boolean)) { throw new
                 * RuntimeException("Expression did not returned a boolean value
                 * but: " + r); } Boolean oldVal =
                 * req.getCallbacks().get(method); Boolean newVal = (Boolean) r;
                 * if ((oldVal == null || !oldVal) && newVal) {
                 * req.getCallbacks().put(method, newVal); method.invoke(obj,
                 * new Object[0]); // TODO: handle return value and sent it as
                 * the answer } } catch (Exception e) { throw new
                 * RuntimeException("Unable to invoke callback", e); } }
                 */
            }
        });
    }

    protected void consumerInjection(Method method, Object bean) {
        MessageDriven annotation = method.getAnnotation(MessageDriven.class);
        if (annotation != null) {
            LOG.info("Creating a consumer for: " + annotation);
            subscribeMethod(method, bean, annotation.uri(), annotation.name());
        }

        Consume consume = method.getAnnotation(Consume.class);
        if (consume != null) {
            LOG.info("Creating a consumer for: " + consume);
            subscribeMethod(method, bean, consume.uri(), consume.ref());
        }
    }

    protected void subscribeMethod(Method method, Object bean, String endpointUri, String endpointName) {
        // lets bind this method to a listener
        String injectionPointName = method.getName();
        Endpoint endpoint = getEndpointInjection(endpointUri, endpointName, injectionPointName);
        if (endpoint != null) {
            try {
                Processor processor = createConsumerProcessor(bean, method, endpoint);
                LOG.info("Created processor: " + processor);
                Consumer consumer = endpoint.createConsumer(processor);
                startService(consumer);
            } catch (Exception e) {
                LOG.warn(e);
                throw wrapRuntimeCamelException(e);
            }
        }
    }

    protected void startService(Service service) throws Exception {
        camelContext.addService(service);
    }

    /**
     * Create a processor which invokes the given method when an incoming
     * message exchange is received
     */
    protected Processor createConsumerProcessor(final Object pojo, final Method method, final Endpoint endpoint) {
        BeanProcessor answer = new BeanProcessor(pojo, getCamelContext());
        answer.setMethodObject(method);
        return answer;
    }


    /**
     * Creates the object to be injected for an {@link org.apache.camel.EndpointInject} or {@link Produce} injection point
     */
    protected Object getInjectionValue(Class<?> type, String endpointUri, String endpointRef, String injectionPointName) {
        Endpoint endpoint = getEndpointInjection(endpointUri, endpointRef, injectionPointName);
        if (endpoint != null) {
            if (type.isInstance(endpoint)) {
                return endpoint;
            } else if (type.isAssignableFrom(Producer.class)) {
                return createInjectionProducer(endpoint);
            } else if (type.isAssignableFrom(DefaultProducerTemplate.class)) {
                return new DefaultProducerTemplate(getCamelContext(), endpoint);
            } else if (type.isAssignableFrom(PollingConsumer.class)) {
                return createInjectionPollingConsumer(endpoint);
            } else if (type.isInterface()) {
                // lets create a proxy
                try {
                    return ProxyHelper.createProxy(endpoint, type);
                } catch (Exception e) {
                    throw new BeanInstantiationException(type, "Could not instantiate proxy of type " + type.getName() + " on endpoint " + endpoint, e);
                }
            } else {
                throw new IllegalArgumentException("Invalid type: " + type.getName() + " which cannot be injected via @EndpointInject for " + endpoint);
            }
        }
        return null;
    }

    /**
     * Factory method to create a started {@link PollingConsumer} to be injected
     * into a POJO
     */
    protected PollingConsumer createInjectionPollingConsumer(Endpoint endpoint) {
        try {
            PollingConsumer pollingConsumer = endpoint.createPollingConsumer();
            startService(pollingConsumer);
            return pollingConsumer;
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
        }
    }

    /**
     * A Factory method to create a started {@link Producer} to be injected into
     * a POJO
     */
    protected Producer createInjectionProducer(Endpoint endpoint) {
        try {
            Producer producer = endpoint.createProducer();
            startService(producer);
            return producer;
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
        }
    }

    protected Endpoint getEndpointInjection(String uri, String name, String injectionPointName) {
        Endpoint endpoint = null;
        if (isNotNullAndNonEmpty(uri)) {
            endpoint = camelContext.getEndpoint(uri);
        } else {
            if (isNullOrBlank(name)) {
                name = injectionPointName;
            }
            endpoint = (Endpoint) applicationContext.getBean(name);
            if (endpoint == null) {
                throw new NoSuchBeanDefinitionException(name);
            }
        }
        return endpoint;
    }

}
