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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bean post processor which implements the <a href="http://camel.apache.org/bean-integration.html">Bean Integration</a>
 * features in Camel. Features such as the <a href="http://camel.apache.org/bean-injection.html">Bean Injection</a> of objects like
 * {@link org.apache.camel.Endpoint} and
 * {@link org.apache.camel.ProducerTemplate} together with support for
 * <a href="http://camel.apache.org/pojo-consuming.html">POJO Consuming</a> via the
 * {@link org.apache.camel.Consume} annotation along with
 * <a href="http://camel.apache.org/pojo-producing.html">POJO Producing</a> via the
 * {@link org.apache.camel.Produce} annotation along with other annotations such as
 * {@link org.apache.camel.DynamicRouter} for creating <a href="http://camel.apache.org/dynamicrouter-annotation.html">a Dynamic router via annotations</a>.
 * {@link org.apache.camel.RecipientList} for creating <a href="http://camel.apache.org/recipientlist-annotation.html">a Recipient List router via annotations</a>.
 * {@link org.apache.camel.RoutingSlip} for creating <a href="http://camel.apache.org/routingslip-annotation.html">a Routing Slip router via annotations</a>.
 * <p/>
 * Components such as <tt>camel-spring</tt>, and <tt>camel-blueprint</tt> can leverage this post processor to hook in Camel
 * bean post processing into their bean processing framework.
 */
public class DefaultCamelBeanPostProcessor {

    protected static final transient Logger LOG = LoggerFactory.getLogger(DefaultCamelBeanPostProcessor.class);
    protected CamelPostProcessorHelper camelPostProcessorHelper;
    protected CamelContext camelContext;

    public DefaultCamelBeanPostProcessor() {
    }

    public DefaultCamelBeanPostProcessor(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Apply this post processor to the given new bean instance <i>before</i> any bean
     * initialization callbacks (like <code>afterPropertiesSet</code>
     * or a custom init-method). The bean will already be populated with property values.
     * The returned bean instance may be a wrapper around the original.
     * 
     * @param bean the new bean instance
     * @param beanName the name of the bean
     * @return the bean instance to use, either the original or a wrapped one; if
     * <code>null</code>, no subsequent BeanPostProcessors will be invoked
     * @throws Exception is thrown if error post processing bean
     */
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws Exception {
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

    /**
     * Apply this post processor to the given new bean instance <i>after</i> any bean
     * initialization callbacks (like <code>afterPropertiesSet</code>
     * or a custom init-method). The bean will already be populated with property values.
     * The returned bean instance may be a wrapper around the original.
     * 
     * @param bean the new bean instance
     * @param beanName the name of the bean
     * @return the bean instance to use, either the original or a wrapped one; if
     * <code>null</code>, no subsequent BeanPostProcessors will be invoked
     * @throws Exception is thrown if error post processing bean
     */
    public Object postProcessAfterInitialization(Object bean, String beanName) throws Exception {
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

    /**
     * Strategy to get the {@link CamelContext} to use.
     */
    public CamelContext getOrLookupCamelContext() {
        return camelContext;
    }

    /**
     * Strategy to get the {@link CamelPostProcessorHelper}
     */
    protected CamelPostProcessorHelper getPostProcessorHelper() {
        if (camelPostProcessorHelper == null) {
            camelPostProcessorHelper = new CamelPostProcessorHelper(getOrLookupCamelContext());
        }
        return camelPostProcessorHelper;
    }

    protected boolean canPostProcessBean(Object bean, String beanName) {
        return bean != null;
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
        ReflectionHelper.doWithFields(bean.getClass(), new ReflectionHelper.FieldCallback() {
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
                if (endpointInject != null && getPostProcessorHelper().matchContext(endpointInject.context())) {
                    injectField(field, endpointInject.uri(), endpointInject.ref(), bean, beanName);
                }

                Produce produce = field.getAnnotation(Produce.class);
                if (produce != null && getPostProcessorHelper().matchContext(produce.context())) {
                    injectField(field, produce.uri(), produce.ref(), bean, beanName);
                }
            }
        });
    }

    protected void injectField(Field field, String endpointUri, String endpointRef, Object bean, String beanName) {
        ReflectionHelper.setField(field, bean, getPostProcessorHelper().getInjectionValue(field.getType(), endpointUri, endpointRef, field.getName(), bean, beanName));
    }

    protected void injectMethods(final Object bean, final String beanName) {
        ReflectionHelper.doWithMethods(bean.getClass(), new ReflectionHelper.MethodCallback() {
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                setterInjection(method, bean, beanName);
                getPostProcessorHelper().consumerInjection(method, bean, beanName);
            }
        });
    }

    protected void setterInjection(Method method, Object bean, String beanName) {
        EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
        if (endpointInject != null && getPostProcessorHelper().matchContext(endpointInject.context())) {
            setterInjection(method, bean, beanName, endpointInject.uri(), endpointInject.ref());
        }

        Produce produce = method.getAnnotation(Produce.class);
        if (produce != null && getPostProcessorHelper().matchContext(produce.context())) {
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
                Object value = getPostProcessorHelper().getInjectionValue(parameterTypes[0], endpointUri, endpointRef, propertyName, bean, beanName);
                ObjectHelper.invokeMethod(method, bean, value);
            }
        }
    }

}
