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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
import org.apache.camel.BeanInject;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.PropertyInject;
import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.blueprint.BlueprintModelJAXBContextFactory;
import org.apache.camel.blueprint.CamelContextFactoryBean;
import org.apache.camel.blueprint.CamelEndpointFactoryBean;
import org.apache.camel.blueprint.CamelRestContextFactoryBean;
import org.apache.camel.blueprint.CamelRouteContextFactoryBean;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.properties.PropertiesComponent;
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
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.blueprint.KeyStoreParametersFactoryBean;
import org.apache.camel.util.blueprint.SSLContextParametersFactoryBean;
import org.apache.camel.util.blueprint.SecureRandomParametersFactoryBean;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.SecureRandomParameters;
import org.osgi.framework.Bundle;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.ComponentDefinitionException;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.osgi.service.blueprint.reflect.RefMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.osgi.service.blueprint.reflect.ComponentMetadata.ACTIVATION_LAZY;
import static org.osgi.service.blueprint.reflect.ServiceReferenceMetadata.AVAILABILITY_MANDATORY;
import static org.osgi.service.blueprint.reflect.ServiceReferenceMetadata.AVAILABILITY_OPTIONAL;

/**
 * Camel {@link NamespaceHandler} to parse the Camel related namespaces.
 */
public class CamelNamespaceHandler implements NamespaceHandler {

    public static final String BLUEPRINT_NS = "http://camel.apache.org/schema/blueprint";
    public static final String SPRING_NS = "http://camel.apache.org/schema/spring";

    private static final String CAMEL_CONTEXT = "camelContext";
    private static final String ROUTE_CONTEXT = "routeContext";
    private static final String REST_CONTEXT = "restContext";
    private static final String ENDPOINT = "endpoint";
    private static final String KEY_STORE_PARAMETERS = "keyStoreParameters";
    private static final String SECURE_RANDOM_PARAMETERS = "secureRandomParameters";
    private static final String SSL_CONTEXT_PARAMETERS = "sslContextParameters";

    private static final Logger LOG = LoggerFactory.getLogger(CamelNamespaceHandler.class);

    private JAXBContext jaxbContext;

    /**
     * Prepares the nodes before parsing.
     */
    public static void doBeforeParse(Node node, String fromNamespace, String toNamespace) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Document doc = node.getOwnerDocument();
            if (node.getNamespaceURI().equals(fromNamespace)) {
                doc.renameNode(node, toNamespace, node.getLocalName());
            }

            // remove whitespace noise from uri, xxxUri attributes, eg new lines, and tabs etc, which allows end users to format
            // their Camel routes in more human readable format, but at runtime those attributes must be trimmed
            // the parser removes most of the noise, but keeps double spaces in the attribute values
            NamedNodeMap map = node.getAttributes();
            for (int i = 0; i < map.getLength(); i++) {
                Node att = map.item(i);
                if (att.getNodeName().equals("uri") || att.getNodeName().endsWith("Uri")) {
                    final String value = att.getNodeValue();
                    String before = ObjectHelper.before(value, "?");
                    String after = ObjectHelper.after(value, "?");

                    if (before != null && after != null) {
                        // remove all double spaces in the uri parameters
                        String changed = after.replaceAll("\\s{2,}", "");
                        if (!after.equals(changed)) {
                            String newAtr = before.trim() + "?" + changed.trim();
                            LOG.debug("Removed whitespace noise from attribute {} -> {}", value, newAtr);
                            att.setNodeValue(newAtr);
                        }
                    }
                }
            }
        }
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); ++i) {
            doBeforeParse(list.item(i), fromNamespace, toNamespace);
        }
    }

    public URL getSchemaLocation(String namespace) {
        if (BLUEPRINT_NS.equals(namespace)) {
            return getClass().getClassLoader().getResource("camel-blueprint.xsd");
        }
        return null;
    }

    @SuppressWarnings({"rawtypes"})
    public Set<Class> getManagedClasses() {
        return new HashSet<Class>(Arrays.asList(BlueprintCamelContext.class));
    }

    public Metadata parse(Element element, ParserContext context) {
        LOG.trace("Parsing element {}", element);

        try {
            // as the camel-core model namespace is Spring we need to rename from blueprint to spring
            doBeforeParse(element, BLUEPRINT_NS, SPRING_NS);

            if (element.getLocalName().equals(CAMEL_CONTEXT)) {
                return parseCamelContextNode(element, context);
            }
            if (element.getLocalName().equals(ROUTE_CONTEXT)) {
                return parseRouteContextNode(element, context);
            }
            if (element.getLocalName().equals(REST_CONTEXT)) {
                return parseRestContextNode(element, context);
            }
            if (element.getLocalName().equals(ENDPOINT)) {
                return parseEndpointNode(element, context);
            }
            if (element.getLocalName().equals(KEY_STORE_PARAMETERS)) {
                return parseKeyStoreParametersNode(element, context);
            }
            if (element.getLocalName().equals(SECURE_RANDOM_PARAMETERS)) {
                return parseSecureRandomParametersNode(element, context);
            }
            if (element.getLocalName().equals(SSL_CONTEXT_PARAMETERS)) {
                return parseSSLContextParametersNode(element, context);
            }
        } finally {
            // make sure to rename back so we leave the DOM as-is
            doBeforeParse(element, SPRING_NS, BLUEPRINT_NS);
        }

        return null;
    }

    private Metadata parseCamelContextNode(Element element, ParserContext context) {
        LOG.trace("Parsing CamelContext {}", element);
        // Find the id, generate one if needed
        String contextId = element.getAttribute("id");
        boolean implicitId = false;

        // let's avoid folks having to explicitly give an ID to a camel context
        if (ObjectHelper.isEmpty(contextId)) {
            // if no explicit id was set then use a default auto generated name
            CamelContextNameStrategy strategy = new DefaultCamelContextNameStrategy();
            contextId = strategy.getName();
            element.setAttributeNS(null, "id", contextId);
            implicitId = true;
        }

        // now let's parse the routes with JAXB
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

        // The properties component is always used / created by the CamelContextFactoryBean
        // so we need to ensure that the resolver is ready to use
        ComponentMetadata propertiesComponentResolver = getComponentResolverReference(context, "properties");

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
        factory2.addDependsOn(propertiesComponentResolver.getId());
        // We need to add other components which the camel context dependsOn
        if (ObjectHelper.isNotEmpty(ccfb.getDependsOn())) {
            factory2.setDependsOn(Arrays.asList(ccfb.getDependsOn().split(" |,")));
        }
        context.getComponentDefinitionRegistry().registerComponentDefinition(factory2);

        MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
        ctx.setId(contextId);
        ctx.setRuntimeClass(BlueprintCamelContext.class);
        ctx.setFactoryComponent(factory2);
        ctx.setFactoryMethod("getContext");
        ctx.setInitMethod("init");
        ctx.setDestroyMethod("destroy");

        // Register factory beans
        registerBeans(context, contextId, ccfb.getThreadPools());
        registerBeans(context, contextId, ccfb.getEndpoints());
        registerBeans(context, contextId, ccfb.getRedeliveryPolicies());
        registerBeans(context, contextId, ccfb.getBeansFactory());

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

        // lets inject the namespaces into any namespace aware POJOs
        injectNamespaces(element, binder);

        LOG.trace("Parsing CamelContext done, returning {}", ctx);
        return ctx;
    }

    protected void injectNamespaces(Element element, Binder<Node> binder) {
        NodeList list = element.getChildNodes();
        Namespaces namespaces = null;
        int size = list.getLength();
        for (int i = 0; i < size; i++) {
            Node child = list.item(i);
            if (child instanceof Element) {
                Element childElement = (Element) child;
                Object object = binder.getJAXBNode(child);
                if (object instanceof NamespaceAware) {
                    NamespaceAware namespaceAware = (NamespaceAware) object;
                    if (namespaces == null) {
                        namespaces = new Namespaces(element);
                    }
                    namespaces.configure(namespaceAware);
                }
                injectNamespaces(childElement, binder);
            }
        }
    }

    private Metadata parseRouteContextNode(Element element, ParserContext context) {
        LOG.trace("Parsing RouteContext {}", element);
        // now parse the routes with JAXB
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
        // must be lazy as we want CamelContext to be activated first
        ctx.setActivation(ACTIVATION_LAZY);

        // lets inject the namespaces into any namespace aware POJOs
        injectNamespaces(element, binder);

        LOG.trace("Parsing RouteContext done, returning {}", element, ctx);
        return ctx;
    }

    private Metadata parseRestContextNode(Element element, ParserContext context) {
        LOG.trace("Parsing RestContext {}", element);
        // now parse the rests with JAXB
        Binder<Node> binder;
        try {
            binder = getJaxbContext().createBinder();
        } catch (JAXBException e) {
            throw new ComponentDefinitionException("Failed to create the JAXB binder : " + e, e);
        }
        Object value = parseUsingJaxb(element, context, binder);
        if (!(value instanceof CamelRestContextFactoryBean)) {
            throw new ComponentDefinitionException("Expected an instance of " + CamelRestContextFactoryBean.class);
        }

        CamelRestContextFactoryBean rcfb = (CamelRestContextFactoryBean) value;
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
        ctx.setFactoryMethod("getRests");
        // must be lazy as we want CamelContext to be activated first
        ctx.setActivation(ACTIVATION_LAZY);

        // lets inject the namespaces into any namespace aware POJOs
        injectNamespaces(element, binder);

        LOG.trace("Parsing RestContext done, returning {}", element, ctx);
        return ctx;
    }

    private Metadata parseEndpointNode(Element element, ParserContext context) {
        LOG.trace("Parsing Endpoint {}", element);
        // now parse the rests with JAXB
        Binder<Node> binder;
        try {
            binder = getJaxbContext().createBinder();
        } catch (JAXBException e) {
            throw new ComponentDefinitionException("Failed to create the JAXB binder : " + e, e);
        }
        Object value = parseUsingJaxb(element, context, binder);
        if (!(value instanceof CamelEndpointFactoryBean)) {
            throw new ComponentDefinitionException("Expected an instance of " + CamelEndpointFactoryBean.class);
        }

        CamelEndpointFactoryBean rcfb = (CamelEndpointFactoryBean) value;
        String id = rcfb.getId();

        MutablePassThroughMetadata factory = context.createMetadata(MutablePassThroughMetadata.class);
        factory.setId(".camelBlueprint.passThrough." + id);
        factory.setObject(new PassThroughCallable<Object>(rcfb));

        MutableBeanMetadata factory2 = context.createMetadata(MutableBeanMetadata.class);
        factory2.setId(".camelBlueprint.factory." + id);
        factory2.setFactoryComponent(factory);
        factory2.setFactoryMethod("call");
        factory2.setInitMethod("afterPropertiesSet");
        factory2.setDestroyMethod("destroy");
        factory2.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));

        MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
        ctx.setId(id);
        ctx.setRuntimeClass(Endpoint.class);
        ctx.setFactoryComponent(factory2);
        ctx.setFactoryMethod("getObject");
        // must be lazy as we want CamelContext to be activated first
        ctx.setActivation(ACTIVATION_LAZY);

        LOG.trace("Parsing endpoint done, returning {}", element, ctx);
        return ctx;
    }

    private Metadata parseKeyStoreParametersNode(Element element, ParserContext context) {
        LOG.trace("Parsing KeyStoreParameters {}", element);
        // now parse the key store parameters with JAXB
        Binder<Node> binder;
        try {
            binder = getJaxbContext().createBinder();
        } catch (JAXBException e) {
            throw new ComponentDefinitionException("Failed to create the JAXB binder : " + e, e);
        }
        Object value = parseUsingJaxb(element, context, binder);
        if (!(value instanceof KeyStoreParametersFactoryBean)) {
            throw new ComponentDefinitionException("Expected an instance of " + KeyStoreParametersFactoryBean.class);
        }

        KeyStoreParametersFactoryBean kspfb = (KeyStoreParametersFactoryBean) value;
        String id = kspfb.getId();

        MutablePassThroughMetadata factory = context.createMetadata(MutablePassThroughMetadata.class);
        factory.setId(".camelBlueprint.passThrough." + id);
        factory.setObject(new PassThroughCallable<Object>(kspfb));

        MutableBeanMetadata factory2 = context.createMetadata(MutableBeanMetadata.class);
        factory2.setId(".camelBlueprint.factory." + id);
        factory2.setFactoryComponent(factory);
        factory2.setFactoryMethod("call");
        factory2.setInitMethod("afterPropertiesSet");
        factory2.setDestroyMethod("destroy");
        factory2.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));

        MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
        ctx.setId(id);
        ctx.setRuntimeClass(KeyStoreParameters.class);
        ctx.setFactoryComponent(factory2);
        ctx.setFactoryMethod("getObject");
        // must be lazy as we want CamelContext to be activated first
        ctx.setActivation(ACTIVATION_LAZY);

        LOG.trace("Parsing KeyStoreParameters done, returning {}", ctx);
        return ctx;
    }

    private Metadata parseSecureRandomParametersNode(Element element, ParserContext context) {
        LOG.trace("Parsing SecureRandomParameters {}", element);
        // now parse the key store parameters with JAXB
        Binder<Node> binder;
        try {
            binder = getJaxbContext().createBinder();
        } catch (JAXBException e) {
            throw new ComponentDefinitionException("Failed to create the JAXB binder : " + e, e);
        }
        Object value = parseUsingJaxb(element, context, binder);
        if (!(value instanceof SecureRandomParametersFactoryBean)) {
            throw new ComponentDefinitionException("Expected an instance of " + SecureRandomParametersFactoryBean.class);
        }

        SecureRandomParametersFactoryBean srfb = (SecureRandomParametersFactoryBean) value;
        String id = srfb.getId();

        MutablePassThroughMetadata factory = context.createMetadata(MutablePassThroughMetadata.class);
        factory.setId(".camelBlueprint.passThrough." + id);
        factory.setObject(new PassThroughCallable<Object>(srfb));

        MutableBeanMetadata factory2 = context.createMetadata(MutableBeanMetadata.class);
        factory2.setId(".camelBlueprint.factory." + id);
        factory2.setFactoryComponent(factory);
        factory2.setFactoryMethod("call");
        factory2.setInitMethod("afterPropertiesSet");
        factory2.setDestroyMethod("destroy");
        factory2.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));

        MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
        ctx.setId(id);
        ctx.setRuntimeClass(SecureRandomParameters.class);
        ctx.setFactoryComponent(factory2);
        ctx.setFactoryMethod("getObject");
        // must be lazy as we want CamelContext to be activated first
        ctx.setActivation(ACTIVATION_LAZY);

        LOG.trace("Parsing SecureRandomParameters done, returning {}", ctx);
        return ctx;
    }

    private Metadata parseSSLContextParametersNode(Element element, ParserContext context) {
        LOG.trace("Parsing SSLContextParameters {}", element);
        // now parse the key store parameters with JAXB
        Binder<Node> binder;
        try {
            binder = getJaxbContext().createBinder();
        } catch (JAXBException e) {
            throw new ComponentDefinitionException("Failed to create the JAXB binder : " + e, e);
        }
        Object value = parseUsingJaxb(element, context, binder);
        if (!(value instanceof SSLContextParametersFactoryBean)) {
            throw new ComponentDefinitionException("Expected an instance of " + SSLContextParametersFactoryBean.class);
        }

        SSLContextParametersFactoryBean scpfb = (SSLContextParametersFactoryBean) value;
        String id = scpfb.getId();

        MutablePassThroughMetadata factory = context.createMetadata(MutablePassThroughMetadata.class);
        factory.setId(".camelBlueprint.passThrough." + id);
        factory.setObject(new PassThroughCallable<Object>(scpfb));

        MutableBeanMetadata factory2 = context.createMetadata(MutableBeanMetadata.class);
        factory2.setId(".camelBlueprint.factory." + id);
        factory2.setFactoryComponent(factory);
        factory2.setFactoryMethod("call");
        factory2.setInitMethod("afterPropertiesSet");
        factory2.setDestroyMethod("destroy");
        factory2.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));

        MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
        ctx.setId(id);
        ctx.setRuntimeClass(SSLContextParameters.class);
        ctx.setFactoryComponent(factory2);
        ctx.setFactoryMethod("getObject");
        // must be lazy as we want CamelContext to be activated first
        ctx.setActivation(ACTIVATION_LAZY);

        LOG.trace("Parsing SSLContextParameters done, returning {}", ctx);
        return ctx;
    }

    private void registerBeans(ParserContext context, String contextId, List<?> beans) {
        if (beans != null) {
            for (Object bean : beans) {
                if (bean instanceof AbstractCamelFactoryBean) {
                    registerBean(context, contextId, (AbstractCamelFactoryBean<?>) bean);
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
        e.addDependsOn(".camelBlueprint.processor.bean." + contextId);

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
            jaxbContext = new BlueprintModelJAXBContextFactory(getClass().getClassLoader()).newJAXBContext();
        }
        return jaxbContext;
    }

    private RefMetadata createRef(ParserContext context, String value) {
        MutableRefMetadata r = context.createMetadata(MutableRefMetadata.class);
        r.setComponentId(value);
        return r;
    }

    private static ComponentMetadata getDataformatResolverReference(ParserContext context, String dataformat) {
        // we cannot resolve dataformat names using property placeholders at this point in time
        if (dataformat.startsWith(PropertiesComponent.DEFAULT_PREFIX_TOKEN)) {
            return null;
        }
        ComponentDefinitionRegistry componentDefinitionRegistry = context.getComponentDefinitionRegistry();
        ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.dataformatResolver." + dataformat);
        if (cm == null) {
            MutableReferenceMetadata svc = context.createMetadata(MutableReferenceMetadata.class);
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
            cm = svc;
        }
        return cm;
    }

    private static ComponentMetadata getLanguageResolverReference(ParserContext context, String language) {
        // we cannot resolve language names using property placeholders at this point in time
        if (language.startsWith(PropertiesComponent.DEFAULT_PREFIX_TOKEN)) {
            return null;
        }
        ComponentDefinitionRegistry componentDefinitionRegistry = context.getComponentDefinitionRegistry();
        ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.languageResolver." + language);
        if (cm == null) {
            MutableReferenceMetadata svc = context.createMetadata(MutableReferenceMetadata.class);
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
            cm = svc;
        }
        return cm;
    }

    private static ComponentMetadata getComponentResolverReference(ParserContext context, String component) {
        // we cannot resolve component names using property placeholders at this point in time
        if (component.startsWith(PropertiesComponent.DEFAULT_PREFIX_TOKEN)) {
            return null;
        }
        ComponentDefinitionRegistry componentDefinitionRegistry = context.getComponentDefinitionRegistry();
        ComponentMetadata cm = componentDefinitionRegistry.getComponentDefinition(".camelBlueprint.componentResolver." + component);
        if (cm == null) {
            MutableReferenceMetadata svc = context.createMetadata(MutableReferenceMetadata.class);
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
            cm = svc;
        }
        return cm;
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

        @Override
        public CamelContext getCamelContext() {
            if (blueprintContainer != null) {
                CamelContext answer = (CamelContext) blueprintContainer.getComponentInstance(camelContextName);
                return answer;
            }
            return null;
        }

        public Object beforeInit(Object bean, String beanName, BeanCreator beanCreator, BeanMetadata beanMetadata) {
            LOG.trace("Before init of bean: {} -> {}", beanName, bean);
            // prefer to inject later in afterInit
            return bean;
        }

        /**
         * A strategy method to allow implementations to perform some custom JBI
         * based injection of the POJO
         *
         * @param bean the bean to be injected
         */
        protected void injectFields(final Object bean, final String beanName) {
            Class<?> clazz = bean.getClass();
            do {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    PropertyInject propertyInject = field.getAnnotation(PropertyInject.class);
                    if (propertyInject != null && matchContext(propertyInject.context())) {
                        injectFieldProperty(field, propertyInject.value(), propertyInject.defaultValue(), bean, beanName);
                    }

                    BeanInject beanInject = field.getAnnotation(BeanInject.class);
                    if (beanInject != null && matchContext(beanInject.context())) {
                        injectFieldBean(field, beanInject.value(), bean, beanName);
                    }

                    EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
                    if (endpointInject != null && matchContext(endpointInject.context())) {
                        injectField(field, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), bean, beanName);
                    }

                    Produce produce = field.getAnnotation(Produce.class);
                    if (produce != null && matchContext(produce.context())) {
                        injectField(field, produce.uri(), produce.ref(), produce.property(), bean, beanName);
                    }
                }
                clazz = clazz.getSuperclass();
            } while (clazz != null && clazz != Object.class);
        }

        protected void injectField(Field field, String endpointUri, String endpointRef, String endpointProperty, Object bean, String beanName) {
            setField(field, bean, getInjectionValue(field.getType(), endpointUri, endpointRef, endpointProperty, field.getName(), bean, beanName));
        }

        protected void injectFieldProperty(Field field, String propertyName, String propertyDefaultValue, Object bean, String beanName) {
            setField(field, bean, getInjectionPropertyValue(field.getType(), propertyName, propertyDefaultValue, field.getName(), bean, beanName));
        }

        public void injectFieldBean(Field field, String name, Object bean, String beanName) {
            setField(field, bean, getInjectionBeanValue(field.getType(), name));
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
            Class<?> clazz = bean.getClass();
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
            PropertyInject propertyInject = method.getAnnotation(PropertyInject.class);
            if (propertyInject != null && matchContext(propertyInject.context())) {
                setterPropertyInjection(method, propertyInject.value(), propertyInject.defaultValue(), bean, beanName);
            }

            BeanInject beanInject = method.getAnnotation(BeanInject.class);
            if (beanInject != null && matchContext(beanInject.context())) {
                setterBeanInjection(method, beanInject.value(), bean, beanName);
            }

            EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
            if (endpointInject != null && matchContext(endpointInject.context())) {
                setterInjection(method, bean, beanName, endpointInject.uri(), endpointInject.ref(), endpointInject.property());
            }

            Produce produce = method.getAnnotation(Produce.class);
            if (produce != null && matchContext(produce.context())) {
                setterInjection(method, bean, beanName, produce.uri(), produce.ref(), produce.property());
            }
        }

        protected void setterPropertyInjection(Method method, String propertyValue, String propertyDefaultValue, Object bean, String beanName) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes != null) {
                if (parameterTypes.length != 1) {
                    LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: " + method);
                } else {
                    String propertyName = ObjectHelper.getPropertyName(method);
                    Object value = getInjectionPropertyValue(parameterTypes[0], propertyValue, propertyDefaultValue, propertyName, bean, beanName);
                    ObjectHelper.invokeMethod(method, bean, value);
                }
            }
        }

        protected void setterBeanInjection(Method method, String name, Object bean, String beanName) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes != null) {
                if (parameterTypes.length != 1) {
                    LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: " + method);
                } else {
                    Object value = getInjectionBeanValue(parameterTypes[0], name);
                    ObjectHelper.invokeMethod(method, bean, value);
                }
            }
        }

        protected void setterInjection(Method method, Object bean, String beanName, String endpointUri, String endpointRef, String endpointProperty) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes != null) {
                if (parameterTypes.length != 1) {
                    LOG.warn("Ignoring badly annotated method for injection due to incorrect number of parameters: " + method);
                } else {
                    String propertyName = ObjectHelper.getPropertyName(method);
                    Object value = getInjectionValue(parameterTypes[0], endpointUri, endpointRef, endpointProperty, propertyName, bean, beanName);
                    ObjectHelper.invokeMethod(method, bean, value);
                }
            }
        }

        public Object afterInit(Object bean, String beanName, BeanCreator beanCreator, BeanMetadata beanMetadata) {
            LOG.trace("After init of bean: {} -> {}", beanName, bean);
            // we cannot inject CamelContextAware beans as the CamelContext may not be ready
            injectFields(bean, beanName);
            injectMethods(bean, beanName);
            return bean;
        }

        public void beforeDestroy(Object bean, String beanName) {
        }

        public void afterDestroy(Object bean, String beanName) {
        }

        @Override
        protected boolean isSingleton(Object bean, String beanName) {
            if (beanName != null) {
                ComponentMetadata meta = blueprintContainer.getComponentMetadata(beanName);
                if (meta instanceof BeanMetadata) {
                    String scope = ((BeanMetadata) meta).getScope();
                    if (scope != null) {
                        return BeanMetadata.SCOPE_SINGLETON.equals(scope);
                    }
                }
            }
            // fallback to super, which will assume singleton
            // for beans not implementing Camel's IsSingleton interface
            return super.isSingleton(bean, beanName);
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
            CamelContextFactoryBean ccfb = (CamelContextFactoryBean) blueprintContainer.getComponentInstance(".camelBlueprint.factory." + camelContextName);
            CamelContext camelContext = ccfb.getContext();

            Set<String> components = new HashSet<>();
            Set<String> languages = new HashSet<>();
            Set<String> dataformats = new HashSet<>();

            // regular camel routes
            for (RouteDefinition rd : camelContext.getRouteDefinitions()) {
                findInputComponents(rd.getInputs(), components, languages, dataformats);
                findOutputComponents(rd.getOutputs(), components, languages, dataformats);
            }

            // rest services can have embedded routes or a singular to
            for (RestDefinition rd : camelContext.getRestDefinitions()) {
                for (VerbDefinition vd : rd.getVerbs()) {
                    Object o = vd.getToOrRoute();
                    if (o instanceof RouteDefinition) {
                        RouteDefinition route = (RouteDefinition) o;
                        findInputComponents(route.getInputs(), components, languages, dataformats);
                        findOutputComponents(route.getOutputs(), components, languages, dataformats);
                    } else if (o instanceof ToDefinition) {
                        findUriComponent(((ToDefinition) o).getUri(), components);
                    } else if (o instanceof ToDynamicDefinition) {
                        findUriComponent(((ToDynamicDefinition) o).getUri(), components);
                    }
                }
            }

            if (ccfb.getRestConfiguration() != null) {
                // rest configuration may refer to a component to use
                String component = ccfb.getRestConfiguration().getComponent();
                if (component != null) {
                    components.add(component);
                }
                component = ccfb.getRestConfiguration().getApiComponent();
                if (component != null) {
                    components.add(component);
                }

                // check what data formats are used in binding mode
                RestBindingMode mode = ccfb.getRestConfiguration().getBindingMode();
                String json = ccfb.getRestConfiguration().getJsonDataFormat();
                if (json == null && mode != null) {
                    if (RestBindingMode.json.equals(mode) || RestBindingMode.json_xml.equals(mode)) {
                        // jackson is the default json data format
                        json = "json-jackson";
                    }
                }
                if (json != null) {
                    dataformats.add(json);
                }
                String xml = ccfb.getRestConfiguration().getXmlDataFormat();
                if (xml == null && mode != null) {
                    if (RestBindingMode.xml.equals(mode) || RestBindingMode.json_xml.equals(mode)) {
                        // jaxb is the default xml data format
                        dataformats.add("jaxb");
                    }
                }
                if (xml != null) {
                    dataformats.add(xml);
                }
            }

            // We can only add service references to resolvers, but we can't make the factory depends on those
            // because the factory has already been instantiated
            try {
                for (String component : components) {
                    if (camelContext.getComponent(component) == null) {
                        getComponentResolverReference(context, component);
                    } else {
                        LOG.debug("Not creating a service reference for component {} because a component already exists in the Camel Context", component);
                    }
                }
                for (String language : languages) {
                    getLanguageResolverReference(context, language);
                }
                for (String dataformat : dataformats) {
                    getDataformatResolverReference(context, dataformat);
                }
            } catch (UnsupportedOperationException e) {
                LOG.warn("Unable to add dependencies to Camel components OSGi services. "
                    + "The Apache Aries blueprint implementation used is too old and the blueprint bundle cannot see the org.apache.camel.spi package.");
                components.clear();
                languages.clear();
                dataformats.clear();
            }

        }

        private void findInputComponents(List<FromDefinition> defs, Set<String> components, Set<String> languages, Set<String> dataformats) {
            if (defs != null) {
                for (FromDefinition def : defs) {
                    findUriComponent(def.getUri(), components);
                    findSchedulerUriComponent(def.getUri(), components);
                }
            }
        }

        @SuppressWarnings({"rawtypes"})
        private void findOutputComponents(List<ProcessorDefinition<?>> defs, Set<String> components, Set<String> languages, Set<String> dataformats) {
            if (defs != null) {
                for (ProcessorDefinition<?> def : defs) {
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
                        findLanguage(((WireTapDefinition<?>) def).getNewExchangeExpression(), languages);
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
            // if the uri is a placeholder then skip it
            if (uri == null || uri.startsWith(PropertiesComponent.DEFAULT_PREFIX_TOKEN)) {
                return;
            }

            // validate uri here up-front so a meaningful error can be logged for blueprint
            // it will also speed up tests in case of failure
            if (!validateUri(uri)) {
                return;
            }

            String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 2);
            if (splitURI[1] != null) {
                String scheme = splitURI[0];
                components.add(scheme);
            }
        }

        private void findSchedulerUriComponent(String uri, Set<String> components) {

            // the input may use a scheduler which can be quartz or spring
            if (uri != null) {
                try {
                    URI u = new URI(uri);
                    Map<String, Object> parameters = URISupport.parseParameters(u);
                    Object value = parameters.get("scheduler");
                    if (value == null) {
                        value = parameters.get("consumer.scheduler");
                    }
                    if (value != null) {
                        // the scheduler can be quartz2 or spring based, so add reference to camel component
                        // from these components os blueprint knows about the requirement
                        String name = value.toString();
                        if ("quartz2".equals(name)) {
                            components.add("quartz2");
                        } else if ("spring".equals(name)) {
                            components.add("spring-event");
                        }
                    }
                } catch (URISyntaxException e) {
                    // ignore as uri should be already validated at findUriComponent method
                }
            }
        }

        private static boolean validateUri(String uri) {
            try {
                // the same validation as done in DefaultCamelContext#normalizeEndpointUri(String)
                URISupport.normalizeUri(uri);
            } catch (URISyntaxException | UnsupportedEncodingException e) {
                LOG.error("Endpoint URI '" + uri + "' is not valid due to: " + e.getMessage(), e);
                return false;
            }
            return true;
        }
    }

}
