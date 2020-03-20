/*
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
package org.apache.camel.impl.engine;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.apache.camel.BeanConfigInject;
import org.apache.camel.BeanInject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Produce;
import org.apache.camel.PropertyInject;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.apache.camel.support.ObjectHelper.invokeMethod;
import static org.apache.camel.util.ObjectHelper.isEmpty;

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
public class DefaultCamelBeanPostProcessor implements CamelBeanPostProcessor {

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultCamelBeanPostProcessor.class);
    protected CamelPostProcessorHelper camelPostProcessorHelper;
    protected CamelContext camelContext;

    public DefaultCamelBeanPostProcessor() {
    }

    public DefaultCamelBeanPostProcessor(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws Exception {
        LOG.trace("Camel bean processing before initialization for bean: {}", beanName);

        // some beans cannot be post processed at this given time, so we gotta check beforehand
        if (!canPostProcessBean(bean, beanName)) {
            return bean;
        }

        if (bindToRegistrySupported()) {
            injectClass(bean, beanName);
            injectNestedClasses(bean, beanName);
            injectBindToRegistryFields(bean, beanName);
            injectBindToRegistryMethods(bean, beanName);
        }

        injectFields(bean, beanName);
        injectMethods(bean, beanName);

        if (bean instanceof CamelContextAware && canSetCamelContext(bean, beanName)) {
            CamelContextAware contextAware = (CamelContextAware)bean;
            DeferredContextBinding deferredBinding = bean.getClass().getAnnotation(DeferredContextBinding.class);
            CamelContext context = getOrLookupCamelContext();

            if (context == null && deferredBinding == null) {
                LOG.warn("No CamelContext defined yet so cannot inject into bean: {}", beanName);
            } else if (context != null) {
                contextAware.setCamelContext(context);
            }
        }

        return bean;
    }

    @Override
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
    public CamelPostProcessorHelper getPostProcessorHelper() {
        if (camelPostProcessorHelper == null) {
            camelPostProcessorHelper = new CamelPostProcessorHelper(getOrLookupCamelContext());
        }
        return camelPostProcessorHelper;
    }

    protected boolean canPostProcessBean(Object bean, String beanName) {
        if (beanName != null && "properties".equals(beanName)) {
            // we cannot process the properties component
            // its instantiated very eager during creation of camel context
            return false;
        }
        return bean != null;
    }

    /**
     * Whether support for the annotation {@link BindToRegistry} is supported.
     * This is only intended for standalone runtimes such as camel-main, camel-quarkus, etc.
     */
    protected boolean bindToRegistrySupported() {
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
        ReflectionHelper.doWithFields(bean.getClass(), field -> {
            PropertyInject propertyInject = field.getAnnotation(PropertyInject.class);
            if (propertyInject != null) {
                injectFieldProperty(field, propertyInject.value(), propertyInject.defaultValue(), bean, beanName);
            }

            BeanInject beanInject = field.getAnnotation(BeanInject.class);
            if (beanInject != null) {
                injectFieldBean(field, beanInject.value(), bean, beanName);
            }

            BeanConfigInject beanConfigInject = field.getAnnotation(BeanConfigInject.class);
            if (beanConfigInject != null) {
                injectFieldBeanConfig(field, beanConfigInject.value(), bean, beanName);
            }

            EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
            if (endpointInject != null) {
                @SuppressWarnings("deprecation")
                String uri = endpointInject.value().isEmpty() ? endpointInject.uri() : endpointInject.value();
                injectField(field, uri, endpointInject.property(), bean, beanName);
            }

            Produce produce = field.getAnnotation(Produce.class);
            if (produce != null) {
                @SuppressWarnings("deprecation")
                String uri = produce.value().isEmpty() ? produce.uri() : produce.value();
                injectField(field, uri, produce.property(), bean, beanName, produce.binding());
            }
        });
    }

    protected void injectBindToRegistryFields(final Object bean, final String beanName) {
        ReflectionHelper.doWithFields(bean.getClass(), field -> {
            BindToRegistry bind = field.getAnnotation(BindToRegistry.class);
            if (bind != null) {
                bindToRegistry(field, bind.value(), bean, beanName, bind.beanPostProcess());
            }
        });
    }

    public void injectField(Field field, String endpointUri, String endpointProperty,
                               Object bean, String beanName) {
        injectField(field, endpointUri, endpointProperty, bean, beanName, true);
    }

    public void injectField(Field field, String endpointUri, String endpointProperty,
                               Object bean, String beanName, boolean binding) {
        ReflectionHelper.setField(field, bean,
                getPostProcessorHelper().getInjectionValue(field.getType(), endpointUri, endpointProperty,
                        field.getName(), bean, beanName, binding));
    }

    public void injectFieldBean(Field field, String name, Object bean, String beanName) {
        ReflectionHelper.setField(field, bean,
                getPostProcessorHelper().getInjectionBeanValue(field.getType(), name));
    }

    public void injectFieldBeanConfig(Field field, String name, Object bean, String beanName) {
        ReflectionHelper.setField(field, bean,
                getPostProcessorHelper().getInjectionBeanConfigValue(field.getType(), name));
    }

    public void injectFieldProperty(Field field, String propertyName, String propertyDefaultValue, Object bean, String beanName) {
        ReflectionHelper.setField(field, bean,
                getPostProcessorHelper().getInjectionPropertyValue(field.getType(), propertyName, propertyDefaultValue,
                        field.getName(), bean, beanName));
    }

    protected void injectMethods(final Object bean, final String beanName) {
        ReflectionHelper.doWithMethods(bean.getClass(), method -> {
            setterInjection(method, bean, beanName);
            getPostProcessorHelper().consumerInjection(method, bean, beanName);
        });
    }

    protected void injectBindToRegistryMethods(final Object bean, final String beanName) {
        // sort the methods so the simplest are used first

        final List<Method> methods = new ArrayList<>();
        ReflectionHelper.doWithMethods(bean.getClass(), method -> {
            BindToRegistry bind = method.getAnnotation(BindToRegistry.class);
            if (bind != null) {
                methods.add(method);
            }
        });

        // sort methods on shortest number of parameters as we want to process the most simplest first
        methods.sort(Comparator.comparingInt(Method::getParameterCount));

        // then do a more complex sorting where we check inter-dependency among the methods
        methods.sort((m1, m2) -> {
            Class[] types1 = m1.getParameterTypes();
            Class[] types2 = m2.getParameterTypes();

            // favour methods that has no parameters
            if (types1.length == 0 && types2.length == 0) {
                return 0;
            } else if (types1.length == 0) {
                return -1;
            } else if (types2.length == 0) {
                return 1;
            }

            // okay then compare so we favour methods that does not use parameter types that are returned from other methods
            boolean usedByOthers1 = false;
            for (Class clazz : types1) {
                usedByOthers1 |= methods.stream().anyMatch(m -> m.getParameterCount() > 0 && clazz.isAssignableFrom(m.getReturnType()));
            }
            boolean usedByOthers2 = false;
            for (Class clazz : types2) {
                usedByOthers2 |= methods.stream().anyMatch(m -> m.getParameterCount() > 0 && clazz.isAssignableFrom(m.getReturnType()));
            }
            return Boolean.compare(usedByOthers1, usedByOthers2);
        });

        LOG.trace("Discovered {} @BindToRegistry methods", methods.size());

        // bind each method
        methods.forEach(method -> {
            BindToRegistry bind = method.getAnnotation(BindToRegistry.class);
            bindToRegistry(method, bind.value(), bean, beanName, bind.beanPostProcess());
        });
    }

    protected void injectClass(final Object bean, final String beanName) {
        Class<?> clazz = bean.getClass();
        BindToRegistry ann = clazz.getAnnotation(BindToRegistry.class);
        if (ann != null) {
            bindToRegistry(clazz, ann.value(), bean, beanName, ann.beanPostProcess());
        }
    }

    protected void injectNestedClasses(final Object bean, final String beanName) {
        ReflectionHelper.doWithClasses(bean.getClass(), clazz -> {
            BindToRegistry ann = (BindToRegistry) clazz.getAnnotation(BindToRegistry.class);
            if (ann != null) {
                // its a nested class so we dont have a bean instance for it
                bindToRegistry(clazz, ann.value(), null, null, ann.beanPostProcess());
            }
        });
    }

    protected void setterInjection(Method method, Object bean, String beanName) {
        PropertyInject propertyInject = method.getAnnotation(PropertyInject.class);
        if (propertyInject != null) {
            setterPropertyInjection(method, propertyInject.value(), propertyInject.defaultValue(), bean, beanName);
        }

        BeanInject beanInject = method.getAnnotation(BeanInject.class);
        if (beanInject != null) {
            setterBeanInjection(method, beanInject.value(), bean, beanName);
        }

        BeanConfigInject beanConfigInject = method.getAnnotation(BeanConfigInject.class);
        if (beanConfigInject != null) {
            setterBeanConfigInjection(method, beanConfigInject.value(), bean, beanName);
        }

        EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
        if (endpointInject != null) {
            String uri = endpointInject.value().isEmpty() ? endpointInject.uri() : endpointInject.value();
            setterInjection(method, bean, beanName, uri, endpointInject.property());
        }

        Produce produce = method.getAnnotation(Produce.class);
        if (produce != null) {
            String uri = produce.value().isEmpty() ? produce.uri() : produce.value();
            setterInjection(method, bean, beanName, uri, produce.property());
        }
    }

    public void setterInjection(Method method, Object bean, String beanName, String endpointUri, String endpointProperty) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: {}", method);
        } else {
            String propertyName = org.apache.camel.util.ObjectHelper.getPropertyName(method);
            Object value = getPostProcessorHelper().getInjectionValue(parameterTypes[0], endpointUri, endpointProperty,
                    propertyName, bean, beanName);
            invokeMethod(method, bean, value);
        }
    }

    public void setterPropertyInjection(Method method, String propertyValue, String propertyDefaultValue,
                                        Object bean, String beanName) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: {}", method);
        } else {
            String propertyName = org.apache.camel.util.ObjectHelper.getPropertyName(method);
            Object value = getPostProcessorHelper().getInjectionPropertyValue(parameterTypes[0], propertyValue, propertyDefaultValue, propertyName, bean, beanName);
            invokeMethod(method, bean, value);
        }
    }

    public void setterBeanInjection(Method method, String name, Object bean, String beanName) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: {}", method);
        } else {
            Object value = getPostProcessorHelper().getInjectionBeanValue(parameterTypes[0], name);
            invokeMethod(method, bean, value);
        }
    }

    public void setterBeanConfigInjection(Method method, String name, Object bean, String beanName) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: {}", method);
        } else {
            Object value = getPostProcessorHelper().getInjectionBeanConfigValue(parameterTypes[0], name);
            invokeMethod(method, bean, value);
        }
    }

    private void bindToRegistry(Class<?> clazz, String name, Object bean, String beanName, boolean beanPostProcess) {
        if (isEmpty(name)) {
            name = clazz.getSimpleName();
        }
        if (bean == null) {
            // no bean so then create an instance from its type
            bean = getOrLookupCamelContext().getInjector().newInstance(clazz);
        }
        if (beanPostProcess) {
            try {
                getOrLookupCamelContext().adapt(ExtendedCamelContext.class).getBeanPostProcessor().postProcessBeforeInitialization(bean, beanName);
                getOrLookupCamelContext().adapt(ExtendedCamelContext.class).getBeanPostProcessor().postProcessAfterInitialization(bean, beanName);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
        getOrLookupCamelContext().getRegistry().bind(name, bean);
    }

    private void bindToRegistry(Field field, String name, Object bean, String beanName, boolean beanPostProcess) {
        if (isEmpty(name)) {
            name = field.getName();
        }
        Object value = ReflectionHelper.getField(field, bean);
        if (value != null) {
            if (beanPostProcess) {
                try {
                    getOrLookupCamelContext().adapt(ExtendedCamelContext.class).getBeanPostProcessor().postProcessBeforeInitialization(value, beanName);
                    getOrLookupCamelContext().adapt(ExtendedCamelContext.class).getBeanPostProcessor().postProcessAfterInitialization(value, beanName);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            }
            getOrLookupCamelContext().getRegistry().bind(name, value);
        }
    }

    private void bindToRegistry(Method method, String name, Object bean, String beanName, boolean beanPostProcess) {
        if (isEmpty(name)) {
            name = method.getName();
        }
        Class<?> returnType = method.getReturnType();
        if (returnType == null || returnType == Void.TYPE) {
            throw new IllegalArgumentException("@BindToRegistry on class: " + method.getDeclaringClass()
                + " method: " + method.getName() + " with void return type is not allowed");
        }

        Object value;
        Object[] parameters = bindToRegistryParameterMapping(method);
        if (parameters != null) {
            value = invokeMethod(method, bean, parameters);
        } else {
            value = invokeMethod(method, bean);
        }
        if (value != null) {
            if (beanPostProcess) {
                try {
                    getOrLookupCamelContext().adapt(ExtendedCamelContext.class).getBeanPostProcessor().postProcessBeforeInitialization(value, beanName);
                    getOrLookupCamelContext().adapt(ExtendedCamelContext.class).getBeanPostProcessor().postProcessAfterInitialization(value, beanName);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            }
            getOrLookupCamelContext().getRegistry().bind(name, value);
        }
    }

    private Object[] bindToRegistryParameterMapping(Method method) {
        if (method.getParameterCount() == 0) {
            return null;
        }

        // map each parameter if possible
        Object[] parameters = new Object[method.getParameterCount()];
        for (int i = 0; i < method.getParameterCount(); i++) {
            Class<?> type = method.getParameterTypes()[i];
            if (type.isAssignableFrom(CamelContext.class)) {
                parameters[i] = getOrLookupCamelContext();
            } else if (type.isAssignableFrom(Registry.class)) {
                parameters[i] = getOrLookupCamelContext().getRegistry();
            } else if (type.isAssignableFrom(TypeConverter.class)) {
                parameters[i] = getOrLookupCamelContext().getTypeConverter();
            } else {
                // we also support @BeanInject and @PropertyInject annotations
                Annotation[] anns = method.getParameterAnnotations()[i];
                if (anns.length == 1) {
                    // we dont assume there are multiple annotations on the same parameter so grab first
                    Annotation ann = anns[0];
                    if (ann.annotationType() == PropertyInject.class) {
                        PropertyInject pi = (PropertyInject) ann;
                        Object result = getPostProcessorHelper().getInjectionPropertyValue(type, pi.value(), pi.defaultValue(), null, null, null);
                        parameters[i] = result;
                    } else if (ann.annotationType() == BeanConfigInject.class) {
                        BeanConfigInject pi = (BeanConfigInject) ann;
                        Object result = getPostProcessorHelper().getInjectionBeanConfigValue(type, pi.value());
                        parameters[i] = result;
                    } else if (ann.annotationType() == BeanInject.class) {
                        BeanInject bi = (BeanInject) ann;
                        Object result = getPostProcessorHelper().getInjectionBeanValue(type, bi.value());
                        parameters[i] = result;
                    }
                } else {
                    // okay attempt to default to singleton instances from the registry
                    Set<?> instances = getOrLookupCamelContext().getRegistry().findByType(type);
                    if (instances.size() == 1) {
                        parameters[i] = instances.iterator().next();
                    } else if (instances.size() > 1) {
                        // there are multiple instances of the same type, so barf
                        throw new IllegalArgumentException("Multiple beans of the same type: " + type
                                + " exists in the Camel registry. Specify the bean name on @BeanInject to bind to a single bean, at the method: " + method);
                    }
                }
            }

            // each parameter must be mapped
            if (parameters[i] == null) {
                int pos = i + 1;
                throw new IllegalArgumentException("@BindToProperty cannot bind parameter #" + pos + " on method: " + method);
            }
        }

        return parameters;
    }

}
