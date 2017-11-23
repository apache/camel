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
package org.apache.camel.spring.handler;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.core.xml.CamelPropertyPlaceholderDefinition;
import org.apache.camel.core.xml.CamelStreamCachingStrategyDefinition;
import org.apache.camel.impl.DefaultCamelContextNameStrategy;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.CamelConsumerTemplateFactoryBean;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.apache.camel.spring.CamelEndpointFactoryBean;
import org.apache.camel.spring.CamelFluentProducerTemplateFactoryBean;
import org.apache.camel.spring.CamelProducerTemplateFactoryBean;
import org.apache.camel.spring.CamelRedeliveryPolicyFactoryBean;
import org.apache.camel.spring.CamelRestContextFactoryBean;
import org.apache.camel.spring.CamelRouteContextFactoryBean;
import org.apache.camel.spring.CamelThreadPoolFactoryBean;
import org.apache.camel.spring.SpringModelJAXBContextFactory;
import org.apache.camel.spring.remoting.CamelProxyFactoryBean;
import org.apache.camel.spring.remoting.CamelServiceExporter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.spring.KeyStoreParametersFactoryBean;
import org.apache.camel.util.spring.SSLContextParametersFactoryBean;
import org.apache.camel.util.spring.SecureRandomParametersFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;

/**
 * Camel namespace for the spring XML configuration file.
 */
public class CamelNamespaceHandler extends NamespaceHandlerSupport {
    private static final String SPRING_NS = "http://camel.apache.org/schema/spring";
    private static final Logger LOG = LoggerFactory.getLogger(CamelNamespaceHandler.class);
    protected BeanDefinitionParser endpointParser = new EndpointDefinitionParser();
    protected BeanDefinitionParser beanPostProcessorParser = new BeanDefinitionParser(CamelBeanPostProcessor.class, false);
    protected Set<String> parserElementNames = new HashSet<String>();
    protected Map<String, BeanDefinitionParser> parserMap = new HashMap<String, BeanDefinitionParser>();
    
    private JAXBContext jaxbContext;
    private Map<String, BeanDefinition> autoRegisterMap = new HashMap<String, BeanDefinition>();

    /**
     * Prepares the nodes before parsing.
     */
    public static void doBeforeParse(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {

            // ensure namespace with versions etc is renamed to be same namespace so we can parse using this handler
            Document doc = node.getOwnerDocument();
            if (node.getNamespaceURI().startsWith(SPRING_NS + "/v")) {
                doc.renameNode(node, SPRING_NS, node.getNodeName());
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
            doBeforeParse(list.item(i));
        }
    }

    public void init() {
        // register restContext parser
        registerParser("restContext", new RestContextDefinitionParser());
        // register routeContext parser
        registerParser("routeContext", new RouteContextDefinitionParser());
        // register endpoint parser
        registerParser("endpoint", endpointParser);

        addBeanDefinitionParser("keyStoreParameters", KeyStoreParametersFactoryBean.class, true, true);
        addBeanDefinitionParser("secureRandomParameters", SecureRandomParametersFactoryBean.class, true, true);
        registerBeanDefinitionParser("sslContextParameters", new SSLContextParametersFactoryBeanBeanDefinitionParser());

        addBeanDefinitionParser("proxy", CamelProxyFactoryBean.class, true, false);
        addBeanDefinitionParser("template", CamelProducerTemplateFactoryBean.class, true, false);
        addBeanDefinitionParser("fluentTemplate", CamelFluentProducerTemplateFactoryBean.class, true, false);
        addBeanDefinitionParser("consumerTemplate", CamelConsumerTemplateFactoryBean.class, true, false);
        addBeanDefinitionParser("export", CamelServiceExporter.class, true, false);
        addBeanDefinitionParser("threadPool", CamelThreadPoolFactoryBean.class, true, true);
        addBeanDefinitionParser("redeliveryPolicyProfile", CamelRedeliveryPolicyFactoryBean.class, true, true);

        // jmx agent, stream caching, hystrix, service call configurations and property placeholder cannot be used outside of the camel context
        addBeanDefinitionParser("jmxAgent", CamelJMXAgentDefinition.class, false, false);
        addBeanDefinitionParser("streamCaching", CamelStreamCachingStrategyDefinition.class, false, false);
        addBeanDefinitionParser("propertyPlaceholder", CamelPropertyPlaceholderDefinition.class, false, false);

        // error handler could be the sub element of camelContext or defined outside camelContext
        BeanDefinitionParser errorHandlerParser = new ErrorHandlerDefinitionParser();
        registerParser("errorHandler", errorHandlerParser);
        parserMap.put("errorHandler", errorHandlerParser);

        // camel context
        boolean osgi = false;
        Class<?> cl = CamelContextFactoryBean.class;
        // These code will try to detected if we are in the OSGi environment.
        // If so, camel will use the OSGi version of CamelContextFactoryBean to create the CamelContext.
        try {
            // Try to load the BundleActivator first
            Class.forName("org.osgi.framework.BundleActivator");
            Class<?> c = Class.forName("org.apache.camel.osgi.Activator");
            Method mth = c.getDeclaredMethod("getBundle");
            Object bundle = mth.invoke(null);
            if (bundle != null) {
                cl = Class.forName("org.apache.camel.osgi.CamelContextFactoryBean");
                osgi = true;
            }
        } catch (Throwable t) {
            // not running with camel-core-osgi so we fallback to the regular factory bean
            LOG.trace("Cannot find class so assuming not running in OSGi container: " + t.getMessage());
        }
        if (osgi) {
            LOG.info("OSGi environment detected.");
        } 
        LOG.debug("Using {} as CamelContextBeanDefinitionParser", cl.getCanonicalName());
        registerParser("camelContext", new CamelContextBeanDefinitionParser(cl));
    }

    protected void addBeanDefinitionParser(String elementName, Class<?> type, boolean register, boolean assignId) {
        BeanDefinitionParser parser = new BeanDefinitionParser(type, assignId);
        if (register) {
            registerParser(elementName, parser);
        }
        parserMap.put(elementName, parser);
    }

    protected void registerParser(String name, org.springframework.beans.factory.xml.BeanDefinitionParser parser) {
        parserElementNames.add(name);
        registerBeanDefinitionParser(name, parser);
    }

    protected Object parseUsingJaxb(Element element, ParserContext parserContext, Binder<Node> binder) {
        try {
            return binder.unmarshal(element);
        } catch (JAXBException e) {
            throw new BeanDefinitionStoreException("Failed to parse JAXB element", e);
        }
    }

    public JAXBContext getJaxbContext() throws JAXBException {
        if (jaxbContext == null) {
            jaxbContext = new SpringModelJAXBContextFactory().newJAXBContext();
        }
        return jaxbContext;
    }
    
    protected class SSLContextParametersFactoryBeanBeanDefinitionParser extends BeanDefinitionParser {

        public SSLContextParametersFactoryBeanBeanDefinitionParser() {
            super(SSLContextParametersFactoryBean.class, true);
        }

        @Override
        protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
            doBeforeParse(element);
            super.doParse(element, builder);
            
            // Note: prefer to use doParse from parent and postProcess; however, parseUsingJaxb requires 
            // parserContext for no apparent reason.
            Binder<Node> binder;
            try {
                binder = getJaxbContext().createBinder();
            } catch (JAXBException e) {
                throw new BeanDefinitionStoreException("Failed to create the JAXB binder", e);
            }
            
            Object value = parseUsingJaxb(element, parserContext, binder);
            
            if (value instanceof SSLContextParametersFactoryBean) {
                SSLContextParametersFactoryBean bean = (SSLContextParametersFactoryBean)value;
                
                builder.addPropertyValue("cipherSuites", bean.getCipherSuites());
                builder.addPropertyValue("cipherSuitesFilter", bean.getCipherSuitesFilter());
                builder.addPropertyValue("secureSocketProtocols", bean.getSecureSocketProtocols());
                builder.addPropertyValue("secureSocketProtocolsFilter", bean.getSecureSocketProtocolsFilter());
                builder.addPropertyValue("keyManagers", bean.getKeyManagers());
                builder.addPropertyValue("trustManagers", bean.getTrustManagers());
                builder.addPropertyValue("secureRandom", bean.getSecureRandom());
                
                builder.addPropertyValue("clientParameters", bean.getClientParameters());
                builder.addPropertyValue("serverParameters", bean.getServerParameters());
            } else {
                throw new BeanDefinitionStoreException("Parsed type is not of the expected type. Expected "
                                                       + SSLContextParametersFactoryBean.class.getName() + " but found "
                                                       + value.getClass().getName());
            }
        }
    }

    protected class RouteContextDefinitionParser extends BeanDefinitionParser {

        public RouteContextDefinitionParser() {
            super(CamelRouteContextFactoryBean.class, false);
        }

        @Override
        protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
            doBeforeParse(element);
            super.doParse(element, parserContext, builder);

            // now lets parse the routes with JAXB
            Binder<Node> binder;
            try {
                binder = getJaxbContext().createBinder();
            } catch (JAXBException e) {
                throw new BeanDefinitionStoreException("Failed to create the JAXB binder", e);
            }
            Object value = parseUsingJaxb(element, parserContext, binder);

            if (value instanceof CamelRouteContextFactoryBean) {
                CamelRouteContextFactoryBean factoryBean = (CamelRouteContextFactoryBean) value;
                builder.addPropertyValue("routes", factoryBean.getRoutes());
            }

            // lets inject the namespaces into any namespace aware POJOs
            injectNamespaces(element, binder);
        }
    }

    protected class EndpointDefinitionParser extends BeanDefinitionParser {

        public EndpointDefinitionParser() {
            super(CamelEndpointFactoryBean.class, false);
        }

        @Override
        protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
            doBeforeParse(element);
            super.doParse(element, parserContext, builder);

            // now lets parse the routes with JAXB
            Binder<Node> binder;
            try {
                binder = getJaxbContext().createBinder();
            } catch (JAXBException e) {
                throw new BeanDefinitionStoreException("Failed to create the JAXB binder", e);
            }
            Object value = parseUsingJaxb(element, parserContext, binder);

            if (value instanceof CamelEndpointFactoryBean) {
                CamelEndpointFactoryBean factoryBean = (CamelEndpointFactoryBean) value;
                builder.addPropertyValue("properties", factoryBean.getProperties());
            }
        }
    }

    protected class RestContextDefinitionParser extends BeanDefinitionParser {

        public RestContextDefinitionParser() {
            super(CamelRestContextFactoryBean.class, false);
        }

        @Override
        protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
            doBeforeParse(element);
            super.doParse(element, parserContext, builder);

            // now lets parse the routes with JAXB
            Binder<Node> binder;
            try {
                binder = getJaxbContext().createBinder();
            } catch (JAXBException e) {
                throw new BeanDefinitionStoreException("Failed to create the JAXB binder", e);
            }
            Object value = parseUsingJaxb(element, parserContext, binder);

            if (value instanceof CamelRestContextFactoryBean) {
                CamelRestContextFactoryBean factoryBean = (CamelRestContextFactoryBean) value;
                builder.addPropertyValue("rests", factoryBean.getRests());
            }

            // lets inject the namespaces into any namespace aware POJOs
            injectNamespaces(element, binder);
        }
    }

    protected class CamelContextBeanDefinitionParser extends BeanDefinitionParser {

        public CamelContextBeanDefinitionParser(Class<?> type) {
            super(type, false);
        }

        @Override
        protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
            doBeforeParse(element);
            super.doParse(element, parserContext, builder);

            String contextId = element.getAttribute("id");
            boolean implicitId = false;
            boolean registerEndpointIdsFromRoute = false;

            // lets avoid folks having to explicitly give an ID to a camel context
            if (ObjectHelper.isEmpty(contextId)) {
                // if no explicit id was set then use a default auto generated name
                CamelContextNameStrategy strategy = new DefaultCamelContextNameStrategy();
                contextId = strategy.getName();
                element.setAttributeNS(null, "id", contextId);
                implicitId = true;
            }

            // now lets parse the routes with JAXB
            Binder<Node> binder;
            try {
                binder = getJaxbContext().createBinder();
            } catch (JAXBException e) {
                throw new BeanDefinitionStoreException("Failed to create the JAXB binder", e);
            }
            Object value = parseUsingJaxb(element, parserContext, binder);

            if (value instanceof CamelContextFactoryBean) {
                // set the property value with the JAXB parsed value
                CamelContextFactoryBean factoryBean = (CamelContextFactoryBean) value;
                builder.addPropertyValue("id", contextId);
                builder.addPropertyValue("implicitId", implicitId);
                builder.addPropertyValue("restConfiguration", factoryBean.getRestConfiguration());
                builder.addPropertyValue("rests", factoryBean.getRests());
                builder.addPropertyValue("routes", factoryBean.getRoutes());
                builder.addPropertyValue("intercepts", factoryBean.getIntercepts());
                builder.addPropertyValue("interceptFroms", factoryBean.getInterceptFroms());
                builder.addPropertyValue("interceptSendToEndpoints", factoryBean.getInterceptSendToEndpoints());
                builder.addPropertyValue("dataFormats", factoryBean.getDataFormats());
                builder.addPropertyValue("transformers", factoryBean.getTransformers());
                builder.addPropertyValue("validators", factoryBean.getValidators());
                builder.addPropertyValue("onCompletions", factoryBean.getOnCompletions());
                builder.addPropertyValue("onExceptions", factoryBean.getOnExceptions());
                builder.addPropertyValue("builderRefs", factoryBean.getBuilderRefs());
                builder.addPropertyValue("routeRefs", factoryBean.getRouteRefs());
                builder.addPropertyValue("restRefs", factoryBean.getRestRefs());
                builder.addPropertyValue("properties", factoryBean.getProperties());
                builder.addPropertyValue("globalOptions", factoryBean.getGlobalOptions());
                builder.addPropertyValue("packageScan", factoryBean.getPackageScan());
                builder.addPropertyValue("contextScan", factoryBean.getContextScan());
                if (factoryBean.getPackages().length > 0) {
                    builder.addPropertyValue("packages", factoryBean.getPackages());
                }
                builder.addPropertyValue("camelPropertyPlaceholder", factoryBean.getCamelPropertyPlaceholder());
                builder.addPropertyValue("camelJMXAgent", factoryBean.getCamelJMXAgent());
                builder.addPropertyValue("camelStreamCachingStrategy", factoryBean.getCamelStreamCachingStrategy());
                builder.addPropertyValue("threadPoolProfiles", factoryBean.getThreadPoolProfiles());
                builder.addPropertyValue("beansFactory", factoryBean.getBeansFactory());
                builder.addPropertyValue("beans", factoryBean.getBeans());
                builder.addPropertyValue("defaultServiceCallConfiguration", factoryBean.getDefaultServiceCallConfiguration());
                builder.addPropertyValue("serviceCallConfigurations", factoryBean.getServiceCallConfigurations());
                builder.addPropertyValue("defaultHystrixConfiguration", factoryBean.getDefaultHystrixConfiguration());
                builder.addPropertyValue("hystrixConfigurations", factoryBean.getHystrixConfigurations());
                // add any depends-on
                addDependsOn(factoryBean, builder);

                registerEndpointIdsFromRoute = "true".equalsIgnoreCase(factoryBean.getRegisterEndpointIdsFromRoute());
            }

            NodeList list = element.getChildNodes();
            int size = list.getLength();
            for (int i = 0; i < size; i++) {
                Node child = list.item(i);
                if (child instanceof Element) {
                    Element childElement = (Element) child;
                    String localName = child.getLocalName();
                    if (localName.equals("endpoint")) {
                        registerEndpoint(childElement, parserContext, contextId);
                    } else if (localName.equals("routeBuilder")) {
                        addDependsOnToRouteBuilder(childElement, parserContext, contextId);
                    } else {
                        BeanDefinitionParser parser = parserMap.get(localName);
                        if (parser != null) {
                            BeanDefinition definition = parser.parse(childElement, parserContext);
                            String id = childElement.getAttribute("id");
                            if (ObjectHelper.isNotEmpty(id)) {
                                parserContext.registerComponent(new BeanComponentDefinition(definition, id));
                                // set the templates with the camel context
                                if (localName.equals("template") || localName.equals("fluentTemplate") || localName.equals("consumerTemplate")
                                        || localName.equals("proxy") || localName.equals("export")) {
                                    // set the camel context
                                    definition.getPropertyValues().addPropertyValue("camelContext", new RuntimeBeanReference(contextId));
                                }
                            }
                        }
                    }
                }
            }

            if (registerEndpointIdsFromRoute) {
                // register as endpoint defined indirectly in the routes by from/to types having id explicit set
                LOG.debug("Registering endpoint with ids defined in Camel routes");
                registerEndpointsWithIdsDefinedInFromOrToTypes(element, parserContext, contextId, binder);
            }

            // register templates if not already defined
            registerTemplates(element, parserContext, contextId);

            // lets inject the namespaces into any namespace aware POJOs
            injectNamespaces(element, binder);

            // inject bean post processor so we can support @Produce etc.
            // no bean processor element so lets create it by our self
            injectBeanPostProcessor(element, parserContext, contextId, builder);
        }
    }

    protected void addDependsOn(CamelContextFactoryBean factoryBean, BeanDefinitionBuilder builder) {
        String dependsOn = factoryBean.getDependsOn();
        if (ObjectHelper.isNotEmpty(dependsOn)) {
            // comma, whitespace and semi colon is valid separators in Spring depends-on
            String[] depends = dependsOn.split(",|;|\\s");
            if (depends == null) {
                throw new IllegalArgumentException("Cannot separate depends-on, was: " + dependsOn);
            } else {
                for (String depend : depends) {
                    depend = depend.trim();
                    LOG.debug("Adding dependsOn {} to CamelContext({})", depend, factoryBean.getId());
                    builder.addDependsOn(depend);
                }
            }
        }
    }

    private void addDependsOnToRouteBuilder(Element childElement, ParserContext parserContext, String contextId) {
        // setting the depends-on explicitly is required since Spring 3.0
        String routeBuilderName = childElement.getAttribute("ref");
        if (ObjectHelper.isNotEmpty(routeBuilderName)) {
            // set depends-on to the context for a routeBuilder bean
            try {
                BeanDefinition definition = parserContext.getRegistry().getBeanDefinition(routeBuilderName);
                Method getDependsOn = definition.getClass().getMethod("getDependsOn", new Class[]{});
                String[] dependsOn = (String[])getDependsOn.invoke(definition);
                if (dependsOn == null || dependsOn.length == 0) {
                    dependsOn = new String[]{contextId};
                } else {
                    String[] temp = new String[dependsOn.length + 1];
                    System.arraycopy(dependsOn, 0, temp, 0, dependsOn.length);
                    temp[dependsOn.length] = contextId;
                    dependsOn = temp;
                }
                Method method = definition.getClass().getMethod("setDependsOn", String[].class);
                method.invoke(definition, (Object)dependsOn);
            } catch (Exception e) {
                // Do nothing here
            }
        }
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

    protected void injectBeanPostProcessor(Element element, ParserContext parserContext, String contextId, BeanDefinitionBuilder builder) {
        Element childElement = element.getOwnerDocument().createElement("beanPostProcessor");
        element.appendChild(childElement);

        String beanPostProcessorId = contextId + ":beanPostProcessor";
        childElement.setAttribute("id", beanPostProcessorId);
        BeanDefinition definition = beanPostProcessorParser.parse(childElement, parserContext);
        // only register to camel context id as a String. Then we can look it up later
        // otherwise we get a circular reference in spring and it will not allow custom bean post processing
        // see more at CAMEL-1663
        definition.getPropertyValues().addPropertyValue("camelId", contextId);
        builder.addPropertyReference("beanPostProcessor", beanPostProcessorId);
    }

    /**
     * Used for auto registering endpoints from the <tt>from</tt> or <tt>to</tt> DSL if they have an id attribute set
     */
    @Deprecated
    protected void registerEndpointsWithIdsDefinedInFromOrToTypes(Element element, ParserContext parserContext, String contextId, Binder<Node> binder) {
        NodeList list = element.getChildNodes();
        int size = list.getLength();
        for (int i = 0; i < size; i++) {
            Node child = list.item(i);
            if (child instanceof Element) {
                Element childElement = (Element) child;
                Object object = binder.getJAXBNode(child);
                // we only want from/to types to be registered as endpoints
                if (object instanceof FromDefinition || object instanceof SendDefinition) {
                    registerEndpoint(childElement, parserContext, contextId);
                }
                // recursive
                registerEndpointsWithIdsDefinedInFromOrToTypes(childElement, parserContext, contextId, binder);
            }
        }
    }

    /**
     * Used for auto registering producer, fluent producer and consumer templates if not already defined in XML.
     */
    protected void registerTemplates(Element element, ParserContext parserContext, String contextId) {
        boolean template = false;
        boolean fluentTemplate = false;
        boolean consumerTemplate = false;

        NodeList list = element.getChildNodes();
        int size = list.getLength();
        for (int i = 0; i < size; i++) {
            Node child = list.item(i);
            if (child instanceof Element) {
                Element childElement = (Element) child;
                String localName = childElement.getLocalName();
                if ("template".equals(localName)) {
                    template = true;
                } else if ("fluentTemplate".equals(localName)) {
                    fluentTemplate = true;
                } else if ("consumerTemplate".equals(localName)) {
                    consumerTemplate = true;
                }
            }
        }

        if (!template) {
            // either we have not used template before or we have auto registered it already and therefore we
            // need it to allow to do it so it can remove the existing auto registered as there is now a clash id
            // since we have multiple camel contexts
            boolean existing = autoRegisterMap.get("template") != null;
            boolean inUse = false;
            try {
                inUse = parserContext.getRegistry().isBeanNameInUse("template");
            } catch (BeanCreationException e) {
                // Spring Eclipse Tooling may throw an exception when you edit the Spring XML online in Eclipse
                // when the isBeanNameInUse method is invoked, so ignore this and continue (CAMEL-2739)
                LOG.debug("Error checking isBeanNameInUse(template). This exception will be ignored", e);
            }
            if (!inUse || existing) {
                String id = "template";
                // auto create a template
                Element templateElement = element.getOwnerDocument().createElement("template");
                templateElement.setAttribute("id", id);
                BeanDefinitionParser parser = parserMap.get("template");
                BeanDefinition definition = parser.parse(templateElement, parserContext);

                // auto register it
                autoRegisterBeanDefinition(id, definition, parserContext, contextId);
            }
        }

        if (!fluentTemplate) {
            // either we have not used fluentTemplate before or we have auto registered it already and therefore we
            // need it to allow to do it so it can remove the existing auto registered as there is now a clash id
            // since we have multiple camel contexts
            boolean existing = autoRegisterMap.get("fluentTemplate") != null;
            boolean inUse = false;
            try {
                inUse = parserContext.getRegistry().isBeanNameInUse("fluentTemplate");
            } catch (BeanCreationException e) {
                // Spring Eclipse Tooling may throw an exception when you edit the Spring XML online in Eclipse
                // when the isBeanNameInUse method is invoked, so ignore this and continue (CAMEL-2739)
                LOG.debug("Error checking isBeanNameInUse(fluentTemplate). This exception will be ignored", e);
            }
            if (!inUse || existing) {
                String id = "fluentTemplate";
                // auto create a fluentTemplate
                Element templateElement = element.getOwnerDocument().createElement("fluentTemplate");
                templateElement.setAttribute("id", id);
                BeanDefinitionParser parser = parserMap.get("fluentTemplate");
                BeanDefinition definition = parser.parse(templateElement, parserContext);

                // auto register it
                autoRegisterBeanDefinition(id, definition, parserContext, contextId);
            }
        }

        if (!consumerTemplate) {
            // either we have not used template before or we have auto registered it already and therefore we
            // need it to allow to do it so it can remove the existing auto registered as there is now a clash id
            // since we have multiple camel contexts
            boolean existing = autoRegisterMap.get("consumerTemplate") != null;
            boolean inUse = false;
            try {
                inUse = parserContext.getRegistry().isBeanNameInUse("consumerTemplate");
            } catch (BeanCreationException e) {
                // Spring Eclipse Tooling may throw an exception when you edit the Spring XML online in Eclipse
                // when the isBeanNameInUse method is invoked, so ignore this and continue (CAMEL-2739)
                LOG.debug("Error checking isBeanNameInUse(consumerTemplate). This exception will be ignored", e);
            }
            if (!inUse || existing) {
                String id = "consumerTemplate";
                // auto create a template
                Element templateElement = element.getOwnerDocument().createElement("consumerTemplate");
                templateElement.setAttribute("id", id);
                BeanDefinitionParser parser = parserMap.get("consumerTemplate");
                BeanDefinition definition = parser.parse(templateElement, parserContext);

                // auto register it
                autoRegisterBeanDefinition(id, definition, parserContext, contextId);
            }
        }

    }

    private void autoRegisterBeanDefinition(String id, BeanDefinition definition, ParserContext parserContext, String contextId) {
        // it is a bit cumbersome to work with the spring bean definition parser
        // as we kinda need to eagerly register the bean definition on the parser context
        // and then later we might find out that we should not have done that in case we have multiple camel contexts
        // that would have a id clash by auto registering the same bean definition with the same id such as a producer template

        // see if we have already auto registered this id
        BeanDefinition existing = autoRegisterMap.get(id);
        if (existing == null) {
            // no then add it to the map and register it
            autoRegisterMap.put(id, definition);
            parserContext.registerComponent(new BeanComponentDefinition(definition, id));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Registered default: {} with id: {} on camel context: {}", new Object[]{definition.getBeanClassName(), id, contextId});
            }
        } else {
            // ups we have already registered it before with same id, but on another camel context
            // this is not good so we need to remove all traces of this auto registering.
            // end user must manually add the needed XML elements and provide unique ids access all camel context himself.
            LOG.debug("Unregistered default: {} with id: {} as we have multiple camel contexts and they must use unique ids."
                    + " You must define the definition in the XML file manually to avoid id clashes when using multiple camel contexts",
                    definition.getBeanClassName(), id);

            parserContext.getRegistry().removeBeanDefinition(id);
        }
    }

    private void registerEndpoint(Element childElement, ParserContext parserContext, String contextId) {
        String id = childElement.getAttribute("id");
        // must have an id to be registered
        if (ObjectHelper.isNotEmpty(id)) {
            // skip underscore as they are internal naming and should not be registered
            if (id.startsWith("_")) {
                LOG.debug("Skip registering endpoint starting with underscore: {}", id);
                return;
            }
            BeanDefinition definition = endpointParser.parse(childElement, parserContext);
            definition.getPropertyValues().addPropertyValue("camelContext", new RuntimeBeanReference(contextId));
            // Need to add this dependency of CamelContext for Spring 3.0
            try {
                Method method = definition.getClass().getMethod("setDependsOn", String[].class);
                method.invoke(definition, (Object) new String[]{contextId});
            } catch (Exception e) {
                // Do nothing here
            }
            parserContext.registerBeanComponent(new BeanComponentDefinition(definition, id));
        }
    }

}
