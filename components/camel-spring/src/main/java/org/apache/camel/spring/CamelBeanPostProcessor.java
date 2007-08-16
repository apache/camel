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

import org.apache.camel.*;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.spring.util.ReflectionUtils;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.isNotNullAndNonEmpty;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A post processor to perform injection of {@link Endpoint} and
 * {@link Producer} instances together with binding methods annotated with
 * {@link @MessageDriven} to a Camel consumer.
 * 
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "beanPostProcessor")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
    private static final transient Log LOG = LogFactory.getLog(CamelBeanPostProcessor.class);
    @XmlTransient
    private SpringCamelContext camelContext;
    @XmlTransient
    private ApplicationContext applicationContext;

    // private List<Consumer> consumers = new ArrayList<Consumer>();

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
                    ReflectionUtils.setField(field, bean, getEndpointInjectionValue(annotation, field.getType(), field.getName()));
                }
            }
        });
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
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes != null) {
                if (parameterTypes.length != 1) {
                    LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: " + method);
                } else {
                    String propertyName = ObjectHelper.getPropertyName(method);
                    Object value = getEndpointInjectionValue(annoation, parameterTypes[0], propertyName);
                    ObjectHelper.invokeMethod(method, bean, value);
                }
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

            // lets bind this method to a listener
            String injectionPointName = method.getName();
            Endpoint endpoint = getEndpointInjection(annotation.uri(), annotation.name(), injectionPointName);
            if (endpoint != null) {
                try {
                    Processor processor = createConsumerProcessor(bean, method, endpoint);
                    LOG.info("Created processor: " + processor);
                    Consumer consumer = endpoint.createConsumer(processor);
                    consumer.start();
                    onConsumerAdded(consumer);
                } catch (Exception e) {
                    LOG.warn(e);
                    throw new RuntimeCamelException(e);
                }
            }
        }
    }

    /**
     * Create a processor which invokes the given method when an incoming
     * message exchange is received
     */
    protected Processor createConsumerProcessor(final Object pojo, final Method method, final Endpoint endpoint) {
        BeanProcessor answer = new BeanProcessor(pojo, getCamelContext());
        answer.setMethod(method);
        return answer;
    }

    protected void onConsumerAdded(Consumer consumer) {
        LOG.debug("Adding consumer: " + consumer);
    }

    /**
     * Creates the value for the injection point for the given annotation
     */
    protected Object getEndpointInjectionValue(EndpointInject annotation, Class<?> type, String injectionPointName) {
        Endpoint endpoint = getEndpointInjection(annotation.uri(), annotation.name(), injectionPointName);
        if (endpoint != null) {
            if (type.isInstance(endpoint)) {
                return endpoint;
            } else if (type.isAssignableFrom(Producer.class)) {
                return createInjectionProducer(endpoint);
            } else if (type.isAssignableFrom(CamelTemplate.class)) {
                return new CamelTemplate(getCamelContext(), endpoint);
            } else if (type.isAssignableFrom(PollingConsumer.class)) {
                return createInjectionPollingConsumer(endpoint);
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
            pollingConsumer.start();
            return pollingConsumer;
        }
        catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * A Factory method to create a started {@link Producer} to be injected into a POJO
     */
    protected Producer createInjectionProducer(Endpoint endpoint) {
        try {
            Producer producer = endpoint.createProducer();
            producer.start();
            return producer;
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
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
