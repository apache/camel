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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.CamelConsumerTemplateFactoryBean;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.apache.camel.spring.CamelEndpointFactoryBean;
import org.apache.camel.spring.CamelJMXAgentDefinition;
import org.apache.camel.spring.CamelProducerTemplateFactoryBean;
import org.apache.camel.spring.remoting.CamelProxyFactoryBean;
import org.apache.camel.spring.remoting.CamelServiceExporter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.view.ModelFileGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

    private static final Log LOG = LogFactory.getLog(CamelNamespaceHandler.class);
    protected BeanDefinitionParser endpointParser = new BeanDefinitionParser(CamelEndpointFactoryBean.class);
    protected BeanDefinitionParser beanPostProcessorParser = new BeanDefinitionParser(CamelBeanPostProcessor.class);
    protected Set<String> parserElementNames = new HashSet<String>();
    protected Binder<Node> binder;
    private JAXBContext jaxbContext;
    private Map<String, BeanDefinitionParser> parserMap = new HashMap<String, BeanDefinitionParser>();
    private Map<String, BeanDefinition> autoRegisterMap = new HashMap<String, BeanDefinition>();

    public ModelFileGenerator createModelFileGenerator() throws JAXBException {
        return new ModelFileGenerator(getJaxbContext());
    }

    public void init() {
        // These elements parser should be used inside the camel context
        addBeanDefinitionParser("proxy", CamelProxyFactoryBean.class, false);
        addBeanDefinitionParser("template", CamelProducerTemplateFactoryBean.class, false);
        addBeanDefinitionParser("consumerTemplate", CamelConsumerTemplateFactoryBean.class, false);
        addBeanDefinitionParser("export", CamelServiceExporter.class, false);
       
        // jmx agent cannot be used outside of the camel context
        addBeanDefinitionParser("jmxAgent", CamelJMXAgentDefinition.class, false);

        // endpoint
        // This element cannot be used out side of camel context
        addBeanDefinitionParser("endpoint", CamelEndpointFactoryBean.class, false);

        // camel context
        boolean osgi = false;
        Class cl = CamelContextFactoryBean.class;
        try {
            cl = Class.forName("org.apache.camel.osgi.CamelContextFactoryBean");
            osgi = true;
        } catch (Throwable t) {
            // not running with camel-osgi so we fallback to the regular factory bean
            LOG.trace("Cannot find class so assuming not running in OSGI container: " + t.getMessage());
        }

        if (osgi) {
            LOG.info("camel-osgi.jar detected in classpath");
        } else {
            LOG.info("camel-osgi.jar not detected in classpath");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using " + cl.getCanonicalName() + " as CamelContextBeanDefinitionParser");
        }
        registerParser("camelContext", new CamelContextBeanDefinitionParser(cl));
    }

    private void addBeanDefinitionParser(String elementName, Class<?> type, boolean register) {
        BeanDefinitionParser parser = new BeanDefinitionParser(type);
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

    protected void registerScriptParser(String elementName, String engineName) {
        registerParser(elementName, new ScriptDefinitionParser(engineName));
    }

    protected void registerParser(String name, org.springframework.beans.factory.xml.BeanDefinitionParser parser) {
        parserElementNames.add(name);
        registerBeanDefinitionParser(name, parser);
    }

    public Set<String> getParserElementNames() {
        return parserElementNames;
    }

    protected Object parseUsingJaxb(Element element, ParserContext parserContext) {
        try {
            binder = getJaxbContext().createBinder();
            return binder.unmarshal(element);
        } catch (JAXBException e) {
            throw new BeanDefinitionStoreException("Failed to parse JAXB element: " + e, e);
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
        classes.add(org.apache.camel.ExchangePattern.class);
        classes.add(org.apache.camel.model.RouteDefinition.class);
        classes.add(org.apache.camel.model.config.StreamResequencerConfig.class);
        classes.add(org.apache.camel.model.dataformat.DataFormatsDefinition.class);
        classes.add(org.apache.camel.model.language.ExpressionDefinition.class);
        classes.add(org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition.class);
        return classes;
    }

    protected class CamelContextBeanDefinitionParser extends BeanDefinitionParser {
        public CamelContextBeanDefinitionParser(Class type) {
            super(type);
        }

        @Override
        protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
            super.doParse(element, parserContext, builder);

            String contextId = element.getAttribute("id");

            // lets avoid folks having to explicitly give an ID to a camel context
            if (ObjectHelper.isEmpty(contextId)) {
                contextId = "camelContext";
                element.setAttribute("id", contextId);
            }

            // now lets parse the routes with JAXB
            Object value = parseUsingJaxb(element, parserContext);
            
            if (value instanceof CamelContextFactoryBean) {
                // set the property value with the JAXB parsed value
                CamelContextFactoryBean factoryBean = (CamelContextFactoryBean)value;
                builder.addPropertyValue("id", contextId);
                builder.addPropertyValue("routes", factoryBean.getRoutes());
                builder.addPropertyValue("intercepts", factoryBean.getIntercepts());
                builder.addPropertyValue("interceptFroms", factoryBean.getInterceptFroms());
                builder.addPropertyValue("interceptSendToEndpoints", factoryBean.getInterceptSendToEndpoints());
                builder.addPropertyValue("dataFormats", factoryBean.getDataFormats());
                builder.addPropertyValue("onCompletions", factoryBean.getOnCompletions());
                builder.addPropertyValue("onExceptions", factoryBean.getOnExceptions());
                builder.addPropertyValue("builderRefs", factoryBean.getBuilderRefs());
                builder.addPropertyValue("properties", factoryBean.getProperties());
                builder.addPropertyValue("packageScan", factoryBean.getPackageScan());
                if (factoryBean.getPackages().length > 0) {
                    builder.addPropertyValue("packages", factoryBean.getPackages());
                }
            }

            boolean createdBeanPostProcessor = false;
            NodeList list = element.getChildNodes();
            int size = list.getLength();
            for (int i = 0; i < size; i++) {
                Node child = list.item(i);
                if (child instanceof Element) {
                    Element childElement = (Element)child;
                    String localName = child.getLocalName();
                    if (localName.equals("beanPostProcessor")) {
                        createBeanPostProcessor(parserContext, contextId, childElement, builder);
                        createdBeanPostProcessor = true;
                    } else if (localName.equals("endpoint")) {
                        registerEndpoint(childElement, parserContext, contextId);
                    } else {
                        BeanDefinitionParser parser = parserMap.get(localName);
                        if (parser != null) {
                            BeanDefinition definition = parser.parse(childElement, parserContext);
                            String id = childElement.getAttribute("id");
                            if (ObjectHelper.isNotEmpty(id)) {
                                parserContext.registerComponent(new BeanComponentDefinition(definition, id));
                                if (localName.equals("jmxAgent")) {
                                    builder.addPropertyReference("camelJMXAgent", id);
                                }
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
           

            // register as endpoint defined indirectly in the routes by from/to types having id explict set
            registerEndpointsWithIdsDefinedInFromOrToTypes(element, parserContext, contextId);

            // register templates if not already defined
            registerTemplates(element, parserContext, contextId);

            // lets inject the namespaces into any namespace aware POJOs
            injectNamespaces(element);
            if (!createdBeanPostProcessor) {
                // no bean processor element so lets create it by ourself
                Element childElement = element.getOwnerDocument().createElement("beanPostProcessor");
                element.appendChild(childElement);
                createBeanPostProcessor(parserContext, contextId, childElement, builder);
            }
        }

    }

    protected void injectNamespaces(Element element) {
        NodeList list = element.getChildNodes();
        Namespaces namespaces = null;
        int size = list.getLength();
        for (int i = 0; i < size; i++) {
            Node child = list.item(i);
            if (child instanceof Element) {
                Element childElement = (Element)child;
                Object object = binder.getJAXBNode(child);
                if (object instanceof NamespaceAware) {
                    NamespaceAware namespaceAware = (NamespaceAware)object;
                    if (namespaces == null) {
                        namespaces = new Namespaces(element);
                    }
                    namespaces.configure(namespaceAware);
                }
                injectNamespaces(childElement);
            }
        }
    }

    /**
     * Used for auto registering endpoints from the <tt>from</tt> or <tt>to</tt> DSL if they have an id attribute set
     */
    protected void registerEndpointsWithIdsDefinedInFromOrToTypes(Element element, ParserContext parserContext, String contextId) {
        NodeList list = element.getChildNodes();
        int size = list.getLength();
        for (int i = 0; i < size; i++) {
            Node child = list.item(i);
            if (child instanceof Element) {
                Element childElement = (Element)child;
                Object object = binder.getJAXBNode(child);
                // we only want from/to types to be registered as endpoints
                if (object instanceof FromDefinition || object instanceof SendDefinition) {
                    registerEndpoint(childElement, parserContext, contextId);
                }
                // recursive
                registerEndpointsWithIdsDefinedInFromOrToTypes(childElement, parserContext, contextId);
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
                Element childElement = (Element)child;
                String localName = childElement.getLocalName();
                if ("template".equals(localName)) {
                    template = true;
                } else if ("consumerTemplate".equals(localName)) {
                    consumerTemplate = true;
                }
            }
        }

        if (!template) {
            String id = "template";
            // auto create a template
            Element templateElement = element.getOwnerDocument().createElement("template");
            templateElement.setAttribute("id", id);
            BeanDefinitionParser parser = parserMap.get("template");
            BeanDefinition definition = parser.parse(templateElement, parserContext);

            // auto register it
            autoRegisterBeanDefinition(id, definition, parserContext, contextId);
        }

        if (!consumerTemplate) {
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

    private void autoRegisterBeanDefinition(String id, BeanDefinition definition, ParserContext parserContext, String contextId) {
        // it is a bit cumbersome to work with the spring bean definition parser
        // as we kinda need to eagerly register the bean definition on the parser context
        // and then later we might find out that we should not have done that in case we have multiple camel contexts
        // that would have a id clash by auto regsitering the same bean definition with the same id such as a producer template

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
                    + " You must define the defintion in the XML file manually to avoid id clashes when using multiple camel contexts");
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
            parserContext.registerComponent(new BeanComponentDefinition(definition, id));
        }
    }
    
}
