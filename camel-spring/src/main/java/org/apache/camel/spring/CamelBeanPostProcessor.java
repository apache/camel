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
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.MessageDriven;
import org.apache.camel.Processor;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.spring.util.ReflectionUtils;
import org.apache.camel.spring.util.BeanInfo;
import org.apache.camel.spring.util.MethodInvocationStrategy;
import org.apache.camel.spring.util.DefaultMethodInvocationStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A post processor to perform injection of {@link Endpoint} and {@link Producer} instances together with binding
 * methods annotated with {@link @MessageDriven} to a Camel consumer.
 *
 * @version $Revision: 1.1 $
 */
public class CamelBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
    private static final transient Log log = LogFactory.getLog(CamelBeanPostProcessor.class);

    private CamelContext camelContext;
    private ApplicationContext applicationContext;
    private MethodInvocationStrategy invocationStrategy = new DefaultMethodInvocationStrategy();

    public CamelBeanPostProcessor(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        injectBean(bean);
        return bean;
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    // Properties
    //-------------------------------------------------------------------------

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public MethodInvocationStrategy getInvocationStrategy() {
        return invocationStrategy;
    }

    public void setInvocationStrategy(MethodInvocationStrategy invocationStrategy) {
        this.invocationStrategy = invocationStrategy;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * A strategy method to allow implementations to perform some custom JBI based injection of the POJO
     *
     * @param bean the bean to be injected
     */
    protected void injectBean(final Object bean) {
        ReflectionUtils.doWithFields(bean.getClass(), new ReflectionUtils.FieldCallback() {
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                EndpointInject annotation = field.getAnnotation(EndpointInject.class);
                if (annotation != null) {
                    ReflectionUtils.setField(field, bean, getEndpointInjectionValue(field, annotation));
                }
            }
        });
    }

    protected void evaluateCallbacks(final Object bean) {
        ReflectionUtils.doWithMethods(bean.getClass(), new ReflectionUtils.MethodCallback() {
            @SuppressWarnings("unchecked")
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                MessageDriven annotation = method.getAnnotation(MessageDriven.class);
                if (annotation != null) {
                    // lets bind this method to a listener
                    Endpoint endpoint = getEndpointInjection(annotation.uri(), annotation.name());
                    if (endpoint != null) {
                        try {
                            Processor processor = createConsumerProcessor(bean, method, endpoint);
                            Consumer consumer = endpoint.createConsumer(processor);
                            addConsumer(consumer);
                        }
                        catch (Exception e) {
                            throw new RuntimeCamelException(e);
                        }
                    }
                }
                /*

                TODO support callbacks?

                if (method.getAnnotation(Callback.class) != null) {
                    try {
                        Expression e = ExpressionFactory.createExpression(
                                method.getAnnotation(Callback.class).condition());
                        JexlContext jc = JexlHelper.createContext();
                        jc.getVars().put("this", obj);
                        Object r = e.evaluate(jc);
                        if (!(r instanceof Boolean)) {
                            throw new RuntimeException("Expression did not returned a boolean value but: " + r);
                        }
                        Boolean oldVal = req.getCallbacks().get(method);
                        Boolean newVal = (Boolean) r;
                        if ((oldVal == null || !oldVal) && newVal) {
                            req.getCallbacks().put(method, newVal);
                            method.invoke(obj, new Object[0]);
                            // TODO: handle return value and sent it as the answer
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to invoke callback", e);
                    }
                }
                */
            }
        });
    }

    /**
     * Create a processor which invokes the given method when an incoming message exchange is received
     */
    protected Processor createConsumerProcessor(final Object pojo, final Method method, final Endpoint endpoint) {
        final BeanInfo beanInfo = new BeanInfo(pojo.getClass(), invocationStrategy);

        return new Processor() {
            public void process(Exchange exchange) throws Exception {
                MethodInvocation invocation = beanInfo.createInvocation(pojo, exchange);
                try {
                    invocation.proceed();
                }
                catch (Exception e) {
                    throw e;
                }
                catch (Throwable throwable) {
                    throw new Exception(throwable);
                }
            }
        };
    }

    protected void addConsumer(Consumer consumer) {
        log.debug("Adding consumer: " + consumer);
    }

    /**
     * Creates the value for the injection point for the given annotation
     */
    protected Object getEndpointInjectionValue(Field field, EndpointInject annotation) {
        Endpoint endpoint = getEndpointInjection(annotation.uri(), annotation.name());
        if (endpoint != null) {
            if (field.getType().isInstance(endpoint)) {
                return endpoint;
            }
            else if (field.getType().isAssignableFrom(Producer.class)) {
                try {
                    return endpoint.createProducer();
                }
                catch (Exception e) {
                    throw new RuntimeCamelException(e);
                }
            }
        }
        return null;
    }

    protected Endpoint getEndpointInjection(String uri, String name) {
        Endpoint endpoint = null;
        if (uri != null) {
            endpoint = camelContext.getEndpoint(uri);
        }
        else {
            if (name != null) {
                endpoint = (Endpoint) applicationContext.getBean(name);
            }
        }
        return endpoint;
    }
}
