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

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.aries.blueprint.ComponentDefinitionRegistry;
import org.apache.aries.blueprint.ComponentDefinitionRegistryProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.PassThroughMetadata;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.aries.blueprint.mutable.MutablePassThroughMetadata;
import org.apache.aries.blueprint.mutable.MutableReferenceMetadata;
import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.blueprint.CamelContextFactoryBean;
import org.apache.camel.core.xml.AbstractCamelContextFactoryBean;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.ComponentDefinitionException;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;

public class CamelNamespaceHandler implements NamespaceHandler {

    private static final String CAMEL_CONTEXT = "camelContext";

    private static final String SPRING_NS = "http://camel.apache.org/schema/spring";
    private static final String BLUEPRINT_NS = "http://camel.apache.org/schema/blueprint";

    private static final transient Log LOG = LogFactory.getLog(CamelNamespaceHandler.class);

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
        return new HashSet<Class>(Arrays.asList(
                BlueprintCamelContext.class
        ));
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
            try {
                PassThroughMetadata ptm = (PassThroughMetadata) context.getComponentDefinitionRegistry().getComponentDefinition("blueprintContainer");
                ccfb.setBlueprintContainer((BlueprintContainer) ptm.getObject());
                ptm = (PassThroughMetadata) context.getComponentDefinitionRegistry().getComponentDefinition("blueprintBundleContext");
                ccfb.setBundleContext((BundleContext) ptm.getObject());
                ccfb.setImplicitId(implicitId);
            } catch (Exception e) {
                throw new ComponentDefinitionException("Unable to initialize camel context factory", e);
            }

            //
            // gnodet: the initialization of the CamelContextFactoryBean is now done at the end of the blueprint
            //    container creation through the use of a ComponentDefinitionRegistryProcessor (those are called
            //    after all the beans have been initialized.  That's why the calls to #afterPropertiesSet and
            //    #init are commented.
            //       This mechanism is now required because the #afterPropertiesSet method on the CamelContext
            //    will search through the blueprint beans for beans implementing known interfaces such as
            //    LifeCycle strategies, etc... so that they are automatically wired to the CamelContext.
            //    However, Blueprint does not support real factories, so in order to do so, we need to actually
            //    access the beans which lead to a circular exception while looking for the CamelContext itself.
            //

            MutablePassThroughMetadata factory = context.createMetadata(MutablePassThroughMetadata.class);
            factory.setId(".camelBlueprint.passThrough." + contextId);
            factory.setObject(new PassThroughCallable<Object>(value));

            MutableBeanMetadata factory2 = context.createMetadata(MutableBeanMetadata.class);
            factory2.setId(".camelBlueprint.factory." + contextId);
            factory2.setFactoryComponent(factory);
            factory2.setFactoryMethod("call");
//            factory2.setInitMethod("afterPropertiesSet");
            factory2.setDestroyMethod("destroy");

            MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
            ctx.setId(contextId);
            ctx.setFactoryComponent(factory2);
            ctx.setFactoryMethod("getContext");
//            ctx.setInitMethod("init");
            ctx.setDestroyMethod("destroy");

            MutablePassThroughMetadata processorFactory = context.createMetadata(MutablePassThroughMetadata.class);
            processorFactory.setId(".camelBlueprint.processor.passThrough." + contextId);
            processorFactory.setObject(new PassThroughCallable<Object>(new CamelDependenciesFinder(ccfb, context)));

            MutableBeanMetadata processor = context.createMetadata(MutableBeanMetadata.class);
            processor.setId(".camelBlueprint.processor." + contextId);
            processor.setRuntimeClass(ComponentDefinitionRegistryProcessor.class);
            processor.setFactoryComponent(processorFactory);
            processor.setFactoryMethod("call");
            processor.setProcessor(true);
            context.getComponentDefinitionRegistry().registerComponentDefinition( processor );

            return ctx;
        }
        return null;
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

    public static class PassThroughCallable<T> implements Callable<T> {

        private T value;

        public PassThroughCallable(T value) {
            this.value = value;
        }

        public T call() throws Exception {
            return value;
        }
    }

    public static class CamelDependenciesFinder implements ComponentDefinitionRegistryProcessor {

        private final CamelContextFactoryBean ccfb;
        private final ParserContext context;

        public CamelDependenciesFinder(CamelContextFactoryBean ccfb, ParserContext context) {
            this.ccfb = ccfb;
            this.context = context;
        }

        public void process(ComponentDefinitionRegistry componentDefinitionRegistry) {
            try {
                ccfb.afterPropertiesSet();
                ccfb.getContext().init();
            } catch (Exception e) {
                throw new ComponentDefinitionException("Unable to initialize camel context factory", e);
            }

            Set<String> components = new HashSet<String>();
            Set<String> languages = new HashSet<String>();
            Set<String> dataformats = new HashSet<String>();
            Set<String> dependsOn = new HashSet<String>();
            for (RouteDefinition rd : ccfb.getContext().getRouteDefinitions()) {
                findInputComponents(rd.getInputs(), components, languages, dataformats);
                findOutputComponents(rd.getOutputs(), components, languages, dataformats);
            }
            try {
                for (String component : components) {
                    ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.componentResolver."  + component);
                    if (cm == null) {
                        MutableReferenceMetadata svc = createMetadata(MutableReferenceMetadata.class);
                        svc.setId(".camelBlueprint.componentResolver."  + component);
                        svc.setFilter("(component=" + component + ")");
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
                    ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.languageResolver."  + language);
                    if (cm == null) {
                        MutableReferenceMetadata svc = createMetadata(MutableReferenceMetadata.class);
                        svc.setId(".camelBlueprint.languageResolver."  + language);
                        svc.setFilter("(language=" + language + ")");
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
                    ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.dataformatResolver."  + dataformat);
                    if (cm == null) {
                        MutableReferenceMetadata svc = createMetadata(MutableReferenceMetadata.class);
                        svc.setId(".camelBlueprint.dataformatResolver."  + dataformat);
                        svc.setFilter("(dataformat=" + dataformat + ")");
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
                        findLanguage(((ResequenceDefinition) def).getExpressions(), languages);
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
