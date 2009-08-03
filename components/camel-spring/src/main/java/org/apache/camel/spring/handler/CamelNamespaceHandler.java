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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.apache.camel.spring.CamelJMXAgentType;
import org.apache.camel.spring.CamelTemplateFactoryBean;
import org.apache.camel.spring.EndpointFactoryBean;
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

    private static final transient Log LOG = LogFactory.getLog(CamelNamespaceHandler.class);
    protected BeanDefinitionParser endpointParser = new BeanDefinitionParser(EndpointFactoryBean.class);
    protected BeanDefinitionParser beanPostProcessorParser = new BeanDefinitionParser(CamelBeanPostProcessor.class);
    protected Set<String> parserElementNames = new HashSet<String>();
    protected Binder<Node> binder;
    private JAXBContext jaxbContext;
    private Map<String, BeanDefinitionParser> parserMap = new HashMap<String, BeanDefinitionParser>();

    public ModelFileGenerator createModelFileGenerator() throws JAXBException {
        return new ModelFileGenerator(getJaxbContext());
    }

    public void init() {
        // These elements parser should be used inside the camel context
        addBeanDefinitionParser("proxy", CamelProxyFactoryBean.class, false);
        addBeanDefinitionParser("template", CamelTemplateFactoryBean.class, false);
        addBeanDefinitionParser("export", CamelServiceExporter.class, false);

        // jmx agent cannot be used outside of the camel context
        addBeanDefinitionParser("jmxAgent", CamelJMXAgentType.class);

        
        boolean osgi = false;
        Class cl = CamelContextFactoryBean.class;
        try {
            cl = Class.forName("org.apache.camel.osgi.CamelContextFactoryBean");
            osgi = true;
        } catch (Throwable t) {
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
    
    private void addBeanDefinitionParser(String elementName, Class<?> type) {
        addBeanDefinitionParser(elementName, type, true);
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

    protected void registerParser(String name,
                                  org.springframework.beans.factory.xml.BeanDefinitionParser parser) {
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
            /*
             * Unmarshaller unmarshaller =
             * getJaxbContext().createUnmarshaller(); return
             * unmarshaller.unmarshal(element);
             */
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
        classes.add(org.apache.camel.model.RouteType.class);
        classes.add(org.apache.camel.model.config.StreamResequencerConfig.class);     
        classes.add(org.apache.camel.model.dataformat.DataFormatType.class);
        classes.add(org.apache.camel.model.language.ExpressionType.class);
        classes.add(org.apache.camel.model.loadbalancer.LoadBalancerType.class);
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

            // lets avoid folks having to explicitly give an ID to a camel
            // context
            if (ObjectHelper.isNullOrBlank(contextId)) {
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
                builder.addPropertyValue("dataFormats", factoryBean.getDataFormats());
                builder.addPropertyValue("builderRefs", factoryBean.getBuilderRefs());
                builder.addPropertyValue("properties", factoryBean.getProperties());
                if (factoryBean.getPackages().length > 0) {
                    builder.addPropertyValue("packages", factoryBean.getPackages());
                }
            }

            boolean createdBeanPostProcessor = false;
            NodeList list = element.getChildNodes();
            List beans = new ArrayList();
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
                        BeanDefinition definition = endpointParser.parse(childElement, parserContext);
                        String id = childElement.getAttribute("id");
                        if (ObjectHelper.isNotNullAndNonEmpty(id)) {
                            // TODO we can zap this?
                            definition.getPropertyValues()
                                .addPropertyValue("camelContext", new RuntimeBeanReference(contextId));
                            // definition.getPropertyValues().addPropertyValue("context",
                            // builder.getBeanDefinition());
                            parserContext.registerComponent(new BeanComponentDefinition(definition, id));
                        }
                    } else {
                        BeanDefinitionParser parser = parserMap.get(localName);
                        if (parser != null) {
                            BeanDefinition definition = parser.parse(childElement, parserContext);
                            String id = childElement.getAttribute("id");
                            if (ObjectHelper.isNotNullAndNonEmpty(id)) {
                                parserContext.registerComponent(new BeanComponentDefinition(definition, id));
                                if (localName.equals("jmxAgent")) {
                                    builder.addPropertyReference("camelJMXAgent", id);
                                }
                                // set the templates with the camel context 
                                if (localName.equals("template") 
                                    || localName.equals("proxy") || localName.equals("export")) {
                                    // set the camel context 
                                    definition.getPropertyValues().addPropertyValue("camelContext", new RuntimeBeanReference(contextId));
                                }   
                            }
                        }
                    }
                }
            }


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
}
