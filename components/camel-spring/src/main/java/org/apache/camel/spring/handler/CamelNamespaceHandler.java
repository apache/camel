/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring.handler;

import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.apache.camel.spring.EndpointFactoryBean;
import org.apache.camel.spring.handler.BeanDefinitionParser;
import org.apache.camel.spring.handler.ScriptDefinitionParser;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.isNotNullAndNonEmpty;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.JAXBException;
import java.util.HashSet;
import java.util.Set;

public class CamelNamespaceHandler extends NamespaceHandlerSupport {
    public static final String JAXB_PACKAGES = "org.apache.camel.spring:org.apache.camel.model:org.apache.camel.model.language";

    protected BeanDefinitionParser endpointParser = new BeanDefinitionParser(EndpointFactoryBean.class);
    protected BeanDefinitionParser beanPostProcessorParser = new BeanDefinitionParser(CamelBeanPostProcessor.class);
    protected Set<String> parserElementNames = new HashSet<String>();
    private JAXBContext jaxbContext;

    public void init() {
        registerParser("endpoint", endpointParser);

        registerParser("camelContext", new BeanDefinitionParser(CamelContextFactoryBean.class) {
            @Override
            protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
                super.doParse(element, parserContext, builder);

                String contextId = element.getAttribute("id");

                // lets avoid folks having to explicitly give an ID to a camel context
                if (ObjectHelper.isNullOrBlank(contextId)) {
                    contextId = "camelContext";
                    element.setAttribute("id", contextId);
                }

                // now lets parse the routes
                Object value = parseUsingJaxb(element, parserContext);
                if (value instanceof CamelContextFactoryBean) {
                    CamelContextFactoryBean factoryBean = (CamelContextFactoryBean) value;
                    builder.addPropertyValue("routes", factoryBean.getRoutes());

                    if (factoryBean.getPackages().length > 0) {
                        builder.addPropertyValue("packages", factoryBean.getPackages());
                    }
                }

                NodeList list = element.getChildNodes();
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Node child = list.item(i);
                    if (child instanceof Element) {
                        Element childElement = (Element) child;
                        String localName = child.getLocalName();
                        if (localName.equals("beanPostProcessor")) {
                            String beanPostProcessorId = contextId + ":beanPostProcessor";
                            childElement.setAttribute("id", beanPostProcessorId);
                            BeanDefinition definition = beanPostProcessorParser.parse(childElement, parserContext);
                            definition.getPropertyValues().addPropertyValue("camelContext", new RuntimeBeanReference(contextId));
                        }
                        else if (localName.equals("endpoint")) {
                            BeanDefinition definition = endpointParser.parse(childElement, parserContext);
                            String id = childElement.getAttribute("id");
                            if (isNotNullAndNonEmpty(id)) {
                                definition.getPropertyValues().addPropertyValue("context", new RuntimeBeanReference(contextId));
                                //definition.getPropertyValues().addPropertyValue("context", builder.getBeanDefinition());
                                parserContext.registerComponent(new BeanComponentDefinition(definition, id));
                            }
                        }
                    }
                }

            }
        });

        registerParser("xpath", new BeanDefinitionParser(XPathBuilder.class) {
            @Override
            protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
                // lets create a child context
                String xpath = DomUtils.getTextValue(element);
                builder.addConstructorArg(xpath);
                super.doParse(element, parserContext, builder);
                builder.addPropertyValue("namespacesFromDom", element);
            }
        });
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
            Unmarshaller unmarshaller = getJaxbContext().createUnmarshaller();
            return unmarshaller.unmarshal(element);
        }
        catch (JAXBException e) {
            throw new BeanDefinitionStoreException("Failed to parse JAXB element: " + e, e);
        }
    }

    protected JAXBContext getJaxbContext() throws JAXBException {
        if (jaxbContext == null) {
        jaxbContext = JAXBContext.newInstance(JAXB_PACKAGES);
        }
        return jaxbContext;
    }
}