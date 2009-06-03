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

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.dataformat.ArtixDSDataFormat;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.model.dataformat.SerializationDataFormat;
import org.apache.camel.model.dataformat.XMLBeansDataFormat;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RandomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.TopicLoadBalancerDefinition;
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

    protected BeanDefinitionParser endpointParser = new BeanDefinitionParser(CamelEndpointFactoryBean.class);
    protected BeanDefinitionParser beanPostProcessorParser = new BeanDefinitionParser(CamelBeanPostProcessor.class);
    protected Set<String> parserElementNames = new HashSet<String>();
    protected Binder<Node> binder;
    private JAXBContext jaxbContext;
    private Map<String, BeanDefinitionParser> parserMap = new HashMap<String, BeanDefinitionParser>();

    public ModelFileGenerator createModelFileGenerator() throws JAXBException {
        return new ModelFileGenerator(getJaxbContext());
    }

    public void init() {
        // remoting
        addBeanDefinitionParser("proxy", CamelProxyFactoryBean.class);
        addBeanDefinitionParser("template", CamelProducerTemplateFactoryBean.class);
        addBeanDefinitionParser("consumerTemplate", CamelConsumerTemplateFactoryBean.class);
        addBeanDefinitionParser("export", CamelServiceExporter.class);

        // data types
        // TODO: why do we have this for data types, and only these 4 out of the 10+ data types we have in total?
        addBeanDefinitionParser("artixDS", ArtixDSDataFormat.class);
        addBeanDefinitionParser("jaxb", JaxbDataFormat.class);
        addBeanDefinitionParser("serialization", SerializationDataFormat.class);
        addBeanDefinitionParser("xmlBeans", XMLBeansDataFormat.class);

        // load balancers
        addBeanDefinitionParser("roundRobin", RoundRobinLoadBalancerDefinition.class);
        addBeanDefinitionParser("random", RandomLoadBalancerDefinition.class);
        addBeanDefinitionParser("sticky", StickyLoadBalancerDefinition.class);
        addBeanDefinitionParser("topic", TopicLoadBalancerDefinition.class);
        addBeanDefinitionParser("failover", FailoverLoadBalancerDefinition.class);

        // jmx agent
        addBeanDefinitionParser("jmxAgent", CamelJMXAgentDefinition.class);

        // endpoint
        addBeanDefinitionParser("endpoint", CamelEndpointFactoryBean.class);

        // camel context
        Class cl = CamelContextFactoryBean.class;
        try {
            cl = Class.forName("org.apache.camel.osgi.CamelContextFactoryBean");
        } catch (Throwable t) {
            // not running with camel-osgi so we fallback to the regular factory bean
        }
        registerParser("camelContext", new CamelContextBeanDefinitionParser(cl));
    }

    private void addBeanDefinitionParser(String elementName, Class<?> type) {
        BeanDefinitionParser parser = new BeanDefinitionParser(type);
        registerParser(elementName, parser);
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
        classes.add(ExchangePattern.class);
        classes.add(org.apache.camel.model.RouteDefinition.class);
        classes.add(org.apache.camel.model.config.StreamResequencerConfig.class);     
        classes.add(org.apache.camel.model.dataformat.DataFormatDefinition.class);
        classes.add(org.apache.camel.model.language.ExpressionDefinition.class);
        classes.add(org.apache.camel.model.loadbalancer.LoadBalancerDefinition.class);
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

            // now lets parse the routes
            Object value = parseUsingJaxb(element, parserContext);
            if (value instanceof CamelContextFactoryBean) {
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
                            }
                        }

                    }
                }
            }

            // register as endpoint defined indirectly in the routes by from/to types having id explict set
            registerEndpointsWithIdsDefinedInFromOrToTypes(element, parserContext, contextId);

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
     * Uses for auto registering endpoints from the <tt>from</tt> or <tt>to</tt> DSL if they have an id attribute set
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
