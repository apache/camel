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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.core.xml.CamelPropertyPlaceholderDefinition;
import org.apache.camel.impl.DefaultCamelContextNameStrategy;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.CamelConsumerTemplateFactoryBean;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.apache.camel.spring.CamelEndpointFactoryBean;
import org.apache.camel.spring.CamelProducerTemplateFactoryBean;
import org.apache.camel.spring.CamelRedeliveryPolicyFactoryBean;
import org.apache.camel.spring.CamelRouteContextFactoryBean;
import org.apache.camel.spring.CamelThreadPoolFactoryBean;
import org.apache.camel.spring.remoting.CamelProxyFactoryBean;
import org.apache.camel.spring.remoting.CamelServiceExporter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.view.ModelFileGenerator;
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
    protected BeanDefinitionParser endpointParser = new BeanDefinitionParser(CamelEndpointFactoryBean.class, false);
    protected BeanDefinitionParser beanPostProcessorParser = new BeanDefinitionParser(CamelBeanPostProcessor.class, false);
    protected Set<String> parserElementNames = new HashSet<String>();
    private JAXBContext jaxbContext;
    private Map<String, BeanDefinitionParser> parserMap = new HashMap<String, BeanDefinitionParser>();
    private Map<String, BeanDefinition> autoRegisterMap = new HashMap<String, BeanDefinition>();

    public static void renameNamespaceRecursive(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Document doc = node.getOwnerDocument();
            if (node.getNamespaceURI().startsWith(SPRING_NS + "/v")) {
                doc.renameNode(node, SPRING_NS, node.getNodeName());
            }
        }
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); ++i) {
            renameNamespaceRecursive(list.item(i));
        }
    }

    public ModelFileGenerator createModelFileGenerator() throws JAXBException {
        return new ModelFileGenerator(getJaxbContext());
    }

    public void init() {
        // register routeContext parser
        registerParser("routeContext", new RouteContextDefinitionParser());

        addBeanDefinitionParser("proxy", CamelProxyFactoryBean.class, true, false);
        addBeanDefinitionParser("template", CamelProducerTemplateFactoryBean.class, true, false);
        addBeanDefinitionParser("consumerTemplate", CamelConsumerTemplateFactoryBean.class, true, false);
        addBeanDefinitionParser("export", CamelServiceExporter.class, true, false);
        addBeanDefinitionParser("endpoint", CamelEndpointFactoryBean.class, true, false);
        addBeanDefinitionParser("threadPool", CamelThreadPoolFactoryBean.class, true, true);
        addBeanDefinitionParser("redeliveryPolicyProfile", CamelRedeliveryPolicyFactoryBean.class, true, true);

        // jmx agent and property placeholder cannot be used outside of the camel context
        addBeanDefinitionParser("jmxAgent", CamelJMXAgentDefinition.class, false, false);
        addBeanDefinitionParser("propertyPlaceholder", CamelPropertyPlaceholderDefinition.class, false, false);

        // errorhandler could be the sub element of camelContext or defined outside camelContext
        BeanDefinitionParser errorHandlerParser = new ErrorHandlerDefinitionParser();
        registerParser("errorHandler", errorHandlerParser);
        parserMap.put("errorHandler", errorHandlerParser);

        // camel context
        boolean osgi = false;
        Class cl = CamelContextFactoryBean.class;
        try {
            Class<?> c = Class.forName("org.apache.camel.osgi.Activator");
            Method mth = c.getDeclaredMethod("getBundle");
            Object bundle = mth.invoke(null);
            if (bundle != null) {
                cl = Class.forName("org.apache.camel.osgi.CamelContextFactoryBean");
                osgi = true;
            }
        } catch (Throwable t) {
            // not running with camel-osgi so we fallback to the regular factory bean
            LOG.trace("Cannot find class so assuming not running in OSGi container: " + t.getMessage());
        }
        if (osgi) {
            LOG.info("OSGi environment detected.");
        } else {
            LOG.info("OSGi environment not detected.");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Using " + cl.getCanonicalName() + " as CamelContextBeanDefinitionParser");
        }
        registerParser("camelContext", new CamelContextBeanDefinitionParser(cl));
    }

    private void addBeanDefinitionParser(String elementName, Class<?> type, boolean register, boolean assignId) {
        BeanDefinitionParser parser = new BeanDefinitionParser(type, assignId);
        if (register) {
            registerParser(elementName, parser);
        }
        parserMap.put(elementName, parser);
    }

    protected void createBeanPostProcessor(ParserContext parserContext, String contextId, Element childElement, BeanDefinitionBuilder parentBuilder) {
        String beanPostProcessorId = contextId + ":beanPostProcessor";
        childElement.setAttribute("id", beanPostProcessorId);
        BeanDefinition definition = beanPostProcessorParser.parse(childElement, parserContext);
        // only register to camel context id as a String. Then we can look it up later
        // otherwise we get a circular reference in spring and it will not allow custom bean post processing
        // see more at CAMEL-1663
        definition.getPropertyValues().addPropertyValue("camelId", contextId);
        parentBuilder.addPropertyReference("beanPostProcessor", beanPostProcessorId);
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
        classes.add(org.apache.camel.spring.CamelContextFactoryBean.class);
        classes.add(CamelJMXAgentDefinition.class);
        classes.add(org.apache.camel.ExchangePattern.class);
        classes.add(org.apache.camel.model.RouteDefinition.class);
        classes.add(org.apache.camel.model.config.StreamResequencerConfig.class);
        classes.add(org.apache.camel.model.dataformat.DataFormatsDefinition.class);
        classes.add(org.apache.camel.model.language.ExpressionDefinition.class);
        classes.add(org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition.class);
        return classes;
    }

    protected class RouteContextDefinitionParser extends BeanDefinitionParser {

        public RouteContextDefinitionParser() {
            super(CamelRouteContextFactoryBean.class, false);
        }

        @Override
        protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
            renameNamespaceRecursive(element);
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
        }
    }


    protected class CamelContextBeanDefinitionParser extends BeanDefinitionParser {

        public CamelContextBeanDefinitionParser(Class type) {
            super(type, false);
        }

        @Override
        protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
            renameNamespaceRecursive(element);
            super.doParse(element, parserContext, builder);

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
                throw new BeanDefinitionStoreException("Failed to create the JAXB binder", e);
            }
            Object value = parseUsingJaxb(element, parserContext, binder);

            if (value instanceof CamelContextFactoryBean) {
                // set the property value with the JAXB parsed value
                CamelContextFactoryBean factoryBean = (CamelContextFactoryBean) value;
                builder.addPropertyValue("id", contextId);
                builder.addPropertyValue("implicitId", implicitId);
                builder.addPropertyValue("routes", factoryBean.getRoutes());
                builder.addPropertyValue("intercepts", factoryBean.getIntercepts());
                builder.addPropertyValue("interceptFroms", factoryBean.getInterceptFroms());
                builder.addPropertyValue("interceptSendToEndpoints", factoryBean.getInterceptSendToEndpoints());
                builder.addPropertyValue("dataFormats", factoryBean.getDataFormats());
                builder.addPropertyValue("onCompletions", factoryBean.getOnCompletions());
                builder.addPropertyValue("onExceptions", factoryBean.getOnExceptions());
                builder.addPropertyValue("builderRefs", factoryBean.getBuilderRefs());
                builder.addPropertyValue("routeRefs", factoryBean.getRouteRefs());
                builder.addPropertyValue("properties", factoryBean.getProperties());
                builder.addPropertyValue("packageScan", factoryBean.getPackageScan());
                builder.addPropertyValue("contextScan", factoryBean.getContextScan());
                if (factoryBean.getPackages().length > 0) {
                    builder.addPropertyValue("packages", factoryBean.getPackages());
                }
                builder.addPropertyValue("camelPropertyPlaceholder", factoryBean.getCamelPropertyPlaceholder());
                builder.addPropertyValue("camelJMXAgent", factoryBean.getCamelJMXAgent());
                builder.addPropertyValue("threadPoolProfiles", factoryBean.getThreadPoolProfiles());
                // add any depends-on
                addDependsOn(factoryBean, builder);
            }

            boolean createdBeanPostProcessor = false;
            NodeList list = element.getChildNodes();
            int size = list.getLength();
            for (int i = 0; i < size; i++) {
                Node child = list.item(i);
                if (child instanceof Element) {
                    Element childElement = (Element) child;
                    String localName = child.getLocalName();
                    if (localName.equals("beanPostProcessor")) {
                        createBeanPostProcessor(parserContext, contextId, childElement, builder);
                        createdBeanPostProcessor = true;
                    } else if (localName.equals("endpoint")) {
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
                                if (localName.equals("template") || localName.equals("consumerTemplate")
                                        || localName.equals("proxy") || localName.equals("export")) {
                                    // set the camel context
                                    definition.getPropertyValues().addPropertyValue("camelContext", new RuntimeBeanReference(contextId));
                                }
                            }
                        }
                    }
                }
            }

            // register as endpoint defined indirectly in the routes by from/to types having id explicit set
            registerEndpointsWithIdsDefinedInFromOrToTypes(element, parserContext, contextId, binder);

            // register templates if not already defined
            registerTemplates(element, parserContext, contextId);

            // lets inject the namespaces into any namespace aware POJOs
            injectNamespaces(element, binder);
            if (!createdBeanPostProcessor) {
                // no bean processor element so lets create it by our self
                Element childElement = element.getOwnerDocument().createElement("beanPostProcessor");
                element.appendChild(childElement);
                createBeanPostProcessor(parserContext, contextId, childElement, builder);
            }
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
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Adding dependsOn " + depend + " to CamelContext(" + factoryBean.getId() + ")");
                    }
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

    /**
     * Used for auto registering endpoints from the <tt>from</tt> or <tt>to</tt> DSL if they have an id attribute set
     */
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
     * Used for auto registering producer and consumer templates if not already defined in XML.
     */
    protected void registerTemplates(Element element, ParserContext parserContext, String contextId) {
        boolean template = false;
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
                LOG.debug("Registered default: " + definition.getBeanClassName() + " with id: " + id + " on camel context: " + contextId);
            }
        } else {
            // ups we have already registered it before with same id, but on another camel context
            // this is not good so we need to remove all traces of this auto registering.
            // end user must manually add the needed XML elements and provide unique ids access all camel context himself.
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unregistered default: " + definition.getBeanClassName() + " with id: " + id
                        + " as we have multiple camel contexts and they must use unique ids."
                        + " You must define the definition in the XML file manually to avoid id clashes when using multiple camel contexts");
            }

            parserContext.getRegistry().removeBeanDefinition(id);
        }
    }

    private void registerEndpoint(Element childElement, ParserContext parserContext, String contextId) {
        String id = childElement.getAttribute("id");
        // must have an id to be registered
        if (ObjectHelper.isNotEmpty(id)) {
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
