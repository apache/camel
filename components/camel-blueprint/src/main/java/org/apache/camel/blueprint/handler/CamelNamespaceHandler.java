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
package org.apache.camel.blueprint.handler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.osgi.service.blueprint.reflect.ServiceReferenceMetadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.aries.blueprint.BeanProcessor;
import org.apache.aries.blueprint.ComponentDefinitionRegistry;
import org.apache.aries.blueprint.ComponentDefinitionRegistryProcessor;
import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.PassThroughMetadata;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.aries.blueprint.mutable.MutablePassThroughMetadata;
import org.apache.aries.blueprint.mutable.MutableRefMetadata;
import org.apache.aries.blueprint.mutable.MutableReferenceMetadata;
import org.apache.aries.blueprint.mutable.MutableValueMetadata;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.blueprint.CamelContextFactoryBean;
import org.apache.camel.blueprint.CamelRouteContextFactoryBean;
import org.apache.camel.core.xml.AbstractCamelContextFactoryBean;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.impl.DefaultCamelContextNameStrategy;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.util.ObjectHelper;
import org.osgi.framework.Bundle;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.ComponentDefinitionException;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.osgi.service.blueprint.reflect.RefMetadata;
import org.osgi.service.blueprint.reflect.ValueMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.osgi.service.blueprint.reflect.ServiceReferenceMetadata.*;

public class CamelNamespaceHandler implements NamespaceHandler {

    private static final String CAMEL_CONTEXT = "camelContext";
    private static final String ROUTE_CONTEXT = "routeContext";

    private static final String SPRING_NS = "http://camel.apache.org/schema/spring";
    private static final String BLUEPRINT_NS = "http://camel.apache.org/schema/blueprint";

    private static final transient Logger LOG = LoggerFactory.getLogger(CamelNamespaceHandler.class);

    private JAXBContext jaxbContext;

    public static void renameNamespaceRecursive(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Document doc = node.getOwnerDocument();
            if (((Element) node).getNamespaceURI().equals(BLUEPRINT_NS)) {
                doc.renameNode(node, SPRING_NS, node.getNodeName());
            }
        }
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); ++i) {
            renameNamespaceRecursive(list.item(i));
        }
    }

    public URL getSchemaLocation(String namespace) {
        return getClass().getClassLoader().getResource("camel-blueprint.xsd");
    }

    @SuppressWarnings("unchecked")
    public Set<Class> getManagedClasses() {
        return new HashSet<Class>(Arrays.asList(BlueprintCamelContext.class));
    }

    public Metadata parse(Element element, ParserContext context) {
        renameNamespaceRecursive(element);
        if (element.getNodeName().equals(CAMEL_CONTEXT)) {
            // Find the id, generate one if needed
            String contextId = element.getAttribute("id");
            boolean implicitId = false;

            // lets avoid folks having to explicitly give an ID to a camel context
            if (ObjectHelper.isEmpty(contextId)) {
                // if no explicit id was set then use a default auto generated name
                CamelContextNameStrategy strategy = new DefaultCamelContextNameStrategy();
                contextId = strategy.getName();
                element.setAttribute("id", contextId);
                implicitId = true;
            }

            // now lets parse the routes with JAXB
            Binder<Node> binder;
            try {
                binder = getJaxbContext().createBinder();
            } catch (JAXBException e) {
                throw new ComponentDefinitionException("Failed to create the JAXB binder : " + e, e);
            }
            Object value = parseUsingJaxb(element, context, binder);
            if (!(value instanceof CamelContextFactoryBean)) {
                throw new ComponentDefinitionException("Expected an instance of " + CamelContextFactoryBean.class);
            }

            CamelContextFactoryBean ccfb = (CamelContextFactoryBean) value;
            ccfb.setImplicitId(implicitId);

            MutablePassThroughMetadata factory = context.createMetadata(MutablePassThroughMetadata.class);
            factory.setId(".camelBlueprint.passThrough." + contextId);
            factory.setObject(new PassThroughCallable<Object>(value));

            MutableBeanMetadata factory2 = context.createMetadata(MutableBeanMetadata.class);
            factory2.setId(".camelBlueprint.factory." + contextId);
            factory2.setFactoryComponent(factory);
            factory2.setFactoryMethod("call");
            factory2.setInitMethod("afterPropertiesSet");
            factory2.setDestroyMethod("destroy");
            factory2.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
            factory2.addProperty("bundleContext", createRef(context, "blueprintBundleContext"));

            MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
            ctx.setId(contextId);
            ctx.setRuntimeClass(BlueprintCamelContext.class);
            ctx.setFactoryComponent(factory2);
            ctx.setFactoryMethod("getContext");
            ctx.setInitMethod("init");
            ctx.setDestroyMethod("destroy");

            // Register objects
            registerBeans(context, contextId, ccfb.getEndpoints());
            registerBeans(context, contextId, ccfb.getThreadPools());
            registerBeans(context, contextId, ccfb.getBeans());

            // Register processors
            MutablePassThroughMetadata beanProcessorFactory = context.createMetadata(MutablePassThroughMetadata.class);
            beanProcessorFactory.setId(".camelBlueprint.processor.bean.passThrough." + contextId);
            beanProcessorFactory.setObject(new PassThroughCallable<Object>(new CamelInjector(contextId)));

            MutableBeanMetadata beanProcessor = context.createMetadata(MutableBeanMetadata.class);
            beanProcessor.setId(".camelBlueprint.processor.bean." + contextId);
            beanProcessor.setRuntimeClass(CamelInjector.class);
            beanProcessor.setFactoryComponent(beanProcessorFactory);
            beanProcessor.setFactoryMethod("call");
            beanProcessor.setProcessor(true);
            beanProcessor.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
            context.getComponentDefinitionRegistry().registerComponentDefinition(beanProcessor);

            MutablePassThroughMetadata regProcessorFactory = context.createMetadata(MutablePassThroughMetadata.class);
            regProcessorFactory.setId(".camelBlueprint.processor.registry.passThrough." + contextId);
            regProcessorFactory.setObject(new PassThroughCallable<Object>(new CamelDependenciesFinder(contextId, context)));

            MutableBeanMetadata regProcessor = context.createMetadata(MutableBeanMetadata.class);
            regProcessor.setId(".camelBlueprint.processor.registry." + contextId);
            regProcessor.setRuntimeClass(CamelDependenciesFinder.class);
            regProcessor.setFactoryComponent(regProcessorFactory);
            regProcessor.setFactoryMethod("call");
            regProcessor.setProcessor(true);
            regProcessor.addDependsOn(".camelBlueprint.processor.bean." + contextId);
            regProcessor.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
            context.getComponentDefinitionRegistry().registerComponentDefinition(regProcessor);

            return ctx;
        }
        if (element.getNodeName().equals(ROUTE_CONTEXT)) {
            // now lets parse the routes with JAXB
            Binder<Node> binder;
            try {
                binder = getJaxbContext().createBinder();
            } catch (JAXBException e) {
                throw new ComponentDefinitionException("Failed to create the JAXB binder : " + e, e);
            }
            Object value = parseUsingJaxb(element, context, binder);
            if (!(value instanceof CamelRouteContextFactoryBean)) {
                throw new ComponentDefinitionException("Expected an instance of " + CamelRouteContextFactoryBean.class);
            }

            CamelRouteContextFactoryBean rcfb = (CamelRouteContextFactoryBean) value;
            String id = rcfb.getId();

            MutablePassThroughMetadata factory = context.createMetadata(MutablePassThroughMetadata.class);
            factory.setId(".camelBlueprint.passThrough." + id);
            factory.setObject(new PassThroughCallable<Object>(rcfb));

            MutableBeanMetadata factory2 = context.createMetadata(MutableBeanMetadata.class);
            factory2.setId(".camelBlueprint.factory." + id);
            factory2.setFactoryComponent(factory);
            factory2.setFactoryMethod("call");

            MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
            ctx.setId(id);
            ctx.setRuntimeClass(List.class);
            ctx.setFactoryComponent(factory2);
            ctx.setFactoryMethod("getRoutes");

            return ctx;
        }
        return null;
    }

    private void registerBeans(ParserContext context, String contextId, List<?> beans) {
        if (beans != null) {
            for (Object bean : beans) {
                if (bean instanceof AbstractCamelFactoryBean) {
                    registerBean(context, contextId, (AbstractCamelFactoryBean) bean);
                }
            }
        }
    }

    protected void registerBean(ParserContext context, String contextId, AbstractCamelFactoryBean<?> fact) {
        String id = fact.getId();

        fact.setCamelContextId(contextId);

        MutablePassThroughMetadata eff = context.createMetadata(MutablePassThroughMetadata.class);
        eff.setId(".camelBlueprint.bean.passthrough." + id);
        eff.setObject(new PassThroughCallable<Object>(fact));

        MutableBeanMetadata ef = context.createMetadata(MutableBeanMetadata.class);
        ef.setId(".camelBlueprint.bean.factory." + id);
        ef.setFactoryComponent(eff);
        ef.setFactoryMethod("call");
        ef.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
        ef.setInitMethod("afterPropertiesSet");
        ef.setDestroyMethod("destroy");

        MutableBeanMetadata e = context.createMetadata(MutableBeanMetadata.class);
        e.setId(id);
        e.setRuntimeClass(fact.getObjectType());
        e.setFactoryComponent(ef);
        e.setFactoryMethod("getObject");

        context.getComponentDefinitionRegistry().registerComponentDefinition(e);
    }

    protected BlueprintContainer getBlueprintContainer(ParserContext context) {
        PassThroughMetadata ptm = (PassThroughMetadata) context.getComponentDefinitionRegistry().getComponentDefinition("blueprintContainer");
        return (BlueprintContainer) ptm.getObject();
    }

    public ComponentMetadata decorate(Node node, ComponentMetadata component, ParserContext context) {
        return null;
    }

    protected Object parseUsingJaxb(Element element, ParserContext parserContext, Binder<Node> binder) {
        try {
            return binder.unmarshal(element);
        } catch (JAXBException e) {
            throw new ComponentDefinitionException("Failed to parse JAXB element: " + e, e);
        }
    }

    public JAXBContext getJaxbContext() throws JAXBException {
        if (jaxbContext == null) {
            jaxbContext = createJaxbContext();
        }
        return jaxbContext;
    }

    protected JAXBContext createJaxbContext() throws JAXBException {
        StringBuilder packages = new StringBuilder();
        for (Class cl : getJaxbPackages()) {
            if (packages.length() > 0) {
                packages.append(":");
            }
            packages.append(cl.getName().substring(0, cl.getName().lastIndexOf('.')));
        }
        return JAXBContext.newInstance(packages.toString(), getClass().getClassLoader());
    }

    protected Set<Class> getJaxbPackages() {
        Set<Class> classes = new HashSet<Class>();
        classes.add(CamelContextFactoryBean.class);
        classes.add(AbstractCamelContextFactoryBean.class);
        classes.add(org.apache.camel.ExchangePattern.class);
        classes.add(org.apache.camel.model.RouteDefinition.class);
        classes.add(org.apache.camel.model.config.StreamResequencerConfig.class);
        classes.add(org.apache.camel.model.dataformat.DataFormatsDefinition.class);
        classes.add(org.apache.camel.model.language.ExpressionDefinition.class);
        classes.add(org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition.class);
        return classes;
    }

    private ValueMetadata createValue(ParserContext context, String value) {
        MutableValueMetadata v = context.createMetadata(MutableValueMetadata.class);
        v.setStringValue(value);
        return v;
    }

    private RefMetadata createRef(ParserContext context, String value) {
        MutableRefMetadata r = context.createMetadata(MutableRefMetadata.class);
        r.setComponentId(value);
        return r;
    }

    public static class PassThroughCallable<T> implements Callable<T> {

        private T value;

        public PassThroughCallable(T value) {
            this.value = value;
        }

        public T call() throws Exception {
            return value;
        }
    }

    public static class CamelInjector extends CamelPostProcessorHelper implements BeanProcessor {

        private final String camelContextName;
        private BlueprintContainer blueprintContainer;

        public CamelInjector(String camelContextName) {
            this.camelContextName = camelContextName;
        }

        public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
            this.blueprintContainer = blueprintContainer;
        }

        public Object beforeInit(Object bean, String beanName, BeanCreator beanCreator, BeanMetadata beanMetadata) {
            injectFields(bean, beanName);
            injectMethods(bean, beanName);
            if (bean instanceof CamelContextAware) {
                ((CamelContextAware) bean).setCamelContext(getCamelContext());
            }
            return bean;
        }

        @Override
        public CamelContext getCamelContext() {
            return (CamelContext) blueprintContainer.getComponentInstance(camelContextName);
        }

        /**
         * A strategy method to allow implementations to perform some custom JBI
         * based injection of the POJO
         *
         * @param bean the bean to be injected
         */
        protected void injectFields(final Object bean, final String beanName) {
            Class clazz = bean.getClass();
            do {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
                    if (endpointInject != null && matchContext(endpointInject.context())) {
                        injectField(field, endpointInject.uri(), endpointInject.ref(), bean, beanName);
                    }

                    Produce produce = field.getAnnotation(Produce.class);
                    if (produce != null && matchContext(produce.context())) {
                        injectField(field, produce.uri(), produce.ref(), bean, beanName);
                    }
                }
                clazz = clazz.getSuperclass();
            } while (clazz != null && clazz != Object.class);
        }

        protected void injectField(Field field, String endpointUri, String endpointRef, Object bean, String beanName) {
            setField(field, bean, getInjectionValue(field.getType(), endpointUri, endpointRef, field.getName(), bean, beanName));
        }

        protected static void setField(Field field, Object instance, Object value) {
            try {
                boolean oldAccessible = field.isAccessible();
                boolean shouldSetAccessible = !Modifier.isPublic(field.getModifiers()) && !oldAccessible;
                if (shouldSetAccessible) {
                    field.setAccessible(true);
                }
                field.set(instance, value);
                if (shouldSetAccessible) {
                    field.setAccessible(oldAccessible);
                }
            } catch (IllegalArgumentException ex) {
                throw new UnsupportedOperationException("Cannot inject value of class: " + value.getClass() + " into: " + field);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Could not access method: " + ex.getMessage());
            }
        }

        protected void injectMethods(final Object bean, final String beanName) {
            Class clazz = bean.getClass();
            do {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    setterInjection(method, bean, beanName);
                    consumerInjection(method, bean, beanName);
                }
                clazz = clazz.getSuperclass();
            } while (clazz != null && clazz != Object.class);
        }

        protected void setterInjection(Method method, Object bean, String beanName) {
            EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
            if (endpointInject != null && matchContext(endpointInject.context())) {
                setterInjection(method, bean, beanName, endpointInject.uri(), endpointInject.ref());
            }

            Produce produce = method.getAnnotation(Produce.class);
            if (produce != null && matchContext(produce.context())) {
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
                    Object value = getInjectionValue(parameterTypes[0], endpointUri, endpointRef, propertyName, bean, beanName);
                    ObjectHelper.invokeMethod(method, bean, value);
                }
            }
        }

        public Object afterInit(Object o, String s, BeanCreator beanCreator, BeanMetadata beanMetadata) {
            return o;
        }

        public void beforeDestroy(Object o, String s) {
        }

        public void afterDestroy(Object o, String s) {
        }

    }

    public static class CamelDependenciesFinder implements ComponentDefinitionRegistryProcessor {

        private final String camelContextName;
        private final ParserContext context;
        private BlueprintContainer blueprintContainer;

        public CamelDependenciesFinder(String camelContextName, ParserContext context) {
            this.camelContextName = camelContextName;
            this.context = context;
        }

        public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
            this.blueprintContainer = blueprintContainer;
        }

        public void process(ComponentDefinitionRegistry componentDefinitionRegistry) {
            CamelContext camelContext = (CamelContext) blueprintContainer.getComponentInstance(camelContextName);

            Set<String> components = new HashSet<String>();
            Set<String> languages = new HashSet<String>();
            Set<String> dataformats = new HashSet<String>();
            Set<String> dependsOn = new HashSet<String>();
            for (RouteDefinition rd : camelContext.getRouteDefinitions()) {
                findInputComponents(rd.getInputs(), components, languages, dataformats);
                findOutputComponents(rd.getOutputs(), components, languages, dataformats);
            }
            try {
                for (String component : components) {
                    ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.componentResolver." + component);
                    if (cm == null) {
                        MutableReferenceMetadata svc = createMetadata(MutableReferenceMetadata.class);
                        svc.setId(".camelBlueprint.componentResolver." + component);
                        svc.setFilter("(component=" + component + ")");
                        svc.setAvailability(componentDefinitionRegistry.containsComponentDefinition(component) ? AVAILABILITY_OPTIONAL : AVAILABILITY_MANDATORY);
                        try {
                            // Try to set the runtime interface (only with aries blueprint > 0.1
                            svc.getClass().getMethod("setRuntimeInterface", Class.class).invoke(svc, ComponentResolver.class);
                        } catch (Throwable t) {
                            // Check if the bundle can see the class
                            try {
                                PassThroughMetadata ptm = (PassThroughMetadata) componentDefinitionRegistry.getComponentDefinition("blueprintBundle");
                                Bundle b = (Bundle) ptm.getObject();
                                if (b.loadClass(ComponentResolver.class.getName()) != ComponentResolver.class) {
                                    throw new UnsupportedOperationException();
                                }
                                svc.setInterface(ComponentResolver.class.getName());
                            } catch (Throwable t2) {
                                throw new UnsupportedOperationException();
                            }
                        }
                        componentDefinitionRegistry.registerComponentDefinition(svc);
                        dependsOn.add(svc.getId());
                    }
                }
                for (String language : languages) {
                    ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.languageResolver." + language);
                    if (cm == null) {
                        MutableReferenceMetadata svc = createMetadata(MutableReferenceMetadata.class);
                        svc.setId(".camelBlueprint.languageResolver." + language);
                        svc.setFilter("(language=" + language + ")");
                        svc.setAvailability(componentDefinitionRegistry.containsComponentDefinition(language) ? AVAILABILITY_OPTIONAL : AVAILABILITY_MANDATORY);
                        try {
                            // Try to set the runtime interface (only with aries blueprint > 0.1
                            svc.getClass().getMethod("setRuntimeInterface", Class.class).invoke(svc, LanguageResolver.class);
                        } catch (Throwable t) {
                            // Check if the bundle can see the class
                            try {
                                PassThroughMetadata ptm = (PassThroughMetadata) componentDefinitionRegistry.getComponentDefinition("blueprintBundle");
                                Bundle b = (Bundle) ptm.getObject();
                                if (b.loadClass(LanguageResolver.class.getName()) != LanguageResolver.class) {
                                    throw new UnsupportedOperationException();
                                }
                                svc.setInterface(LanguageResolver.class.getName());
                            } catch (Throwable t2) {
                                throw new UnsupportedOperationException();
                            }
                        }
                        componentDefinitionRegistry.registerComponentDefinition(svc);
                        dependsOn.add(svc.getId());
                    }
                }
                for (String dataformat : dataformats) {
                    ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.dataformatResolver." + dataformat);
                    if (cm == null) {
                        MutableReferenceMetadata svc = createMetadata(MutableReferenceMetadata.class);
                        svc.setId(".camelBlueprint.dataformatResolver." + dataformat);
                        svc.setFilter("(dataformat=" + dataformat + ")");
                        svc.setAvailability(componentDefinitionRegistry.containsComponentDefinition(dataformat) ? AVAILABILITY_OPTIONAL : AVAILABILITY_MANDATORY);
                        try {
                            // Try to set the runtime interface (only with aries blueprint > 0.1
                            svc.getClass().getMethod("setRuntimeInterface", Class.class).invoke(svc, DataFormatResolver.class);
                        } catch (Throwable t) {
                            // Check if the bundle can see the class
                            try {
                                PassThroughMetadata ptm = (PassThroughMetadata) componentDefinitionRegistry.getComponentDefinition("blueprintBundle");
                                Bundle b = (Bundle) ptm.getObject();
                                if (b.loadClass(DataFormatResolver.class.getName()) != DataFormatResolver.class) {
                                    throw new UnsupportedOperationException();
                                }
                                svc.setInterface(DataFormatResolver.class.getName());
                            } catch (Throwable t2) {
                                throw new UnsupportedOperationException();
                            }
                        }
                        componentDefinitionRegistry.registerComponentDefinition(svc);
                        dependsOn.add(svc.getId());
                    }
                }
            } catch (UnsupportedOperationException e) {
                LOG.warn("Unable to add dependencies on to camel components OSGi services.  "
                    + "The Apache Aries blueprint implementation used it too old and the blueprint bundle can not see the org.apache.camel.spi package.");
                components.clear();
                languages.clear();
                dataformats.clear();
            }

        }

        public <T extends org.osgi.service.blueprint.reflect.Metadata> T createMetadata(java.lang.Class<T> tClass) {
            return context.createMetadata(tClass);
        }

        private void findInputComponents(List<FromDefinition> defs, Set<String> components, Set<String> languages, Set<String> dataformats) {
            if (defs != null) {
                for (FromDefinition def : defs) {
                    findUriComponent(def.getUri(), components);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void findOutputComponents(List<ProcessorDefinition> defs, Set<String> components, Set<String> languages, Set<String> dataformats) {
            if (defs != null) {
                for (ProcessorDefinition def : defs) {
                    if (def instanceof SendDefinition) {
                        findUriComponent(((SendDefinition) def).getUri(), components);
                    }
                    if (def instanceof MarshalDefinition) {
                        findDataFormat(((MarshalDefinition) def).getDataFormatType(), dataformats);
                    }
                    if (def instanceof UnmarshalDefinition) {
                        findDataFormat(((UnmarshalDefinition) def).getDataFormatType(), dataformats);
                    }
                    if (def instanceof ExpressionNode) {
                        findLanguage(((ExpressionNode) def).getExpression(), languages);
                    }
                    if (def instanceof ResequenceDefinition) {
                        findLanguage(((ResequenceDefinition) def).getExpression(), languages);
                    }
                    if (def instanceof AggregateDefinition) {
                        findLanguage(((AggregateDefinition) def).getExpression(), languages);
                        findLanguage(((AggregateDefinition) def).getCorrelationExpression(), languages);
                        findLanguage(((AggregateDefinition) def).getCompletionPredicate(), languages);
                        findLanguage(((AggregateDefinition) def).getCompletionTimeoutExpression(), languages);
                        findLanguage(((AggregateDefinition) def).getCompletionSizeExpression(), languages);
                    }
                    if (def instanceof CatchDefinition) {
                        findLanguage(((CatchDefinition) def).getHandled(), languages);
                    }
                    if (def instanceof OnExceptionDefinition) {
                        findLanguage(((OnExceptionDefinition) def).getRetryWhile(), languages);
                        findLanguage(((OnExceptionDefinition) def).getHandled(), languages);
                        findLanguage(((OnExceptionDefinition) def).getContinued(), languages);
                    }
                    if (def instanceof SortDefinition) {
                        findLanguage(((SortDefinition) def).getExpression(), languages);
                    }
                    if (def instanceof WireTapDefinition) {
                        findLanguage(((WireTapDefinition) def).getNewExchangeExpression(), languages);
                    }
                    findOutputComponents(def.getOutputs(), components, languages, dataformats);
                }
            }
        }

        private void findLanguage(ExpressionDefinition expression, Set<String> languages) {
            if (expression != null) {
                String lang = expression.getLanguage();
                if (lang != null && lang.length() > 0) {
                    languages.add(lang);
                }
            }
        }

        private void findLanguage(List<ExpressionDefinition> expressions, Set<String> languages) {
            if (expressions != null) {
                for (ExpressionDefinition e : expressions) {
                    findLanguage(e, languages);
                }
            }
        }

        private void findLanguage(ExpressionSubElementDefinition expression, Set<String> languages) {
            if (expression != null) {
                findLanguage(expression.getExpressionType(), languages);
            }
        }

        private void findDataFormat(DataFormatDefinition dfd, Set<String> dataformats) {
            if (dfd != null && dfd.getDataFormatName() != null) {
                dataformats.add(dfd.getDataFormatName());
            }
        }

        private void findUriComponent(String uri, Set<String> components) {
            if (uri != null) {
                String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 2);
                if (splitURI[1] != null) {
                    String scheme = splitURI[0];
                    components.add(scheme);
                }
            }
        }

    }

}
