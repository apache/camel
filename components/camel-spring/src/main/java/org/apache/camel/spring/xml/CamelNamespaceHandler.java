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
package org.apache.camel.spring.xml;

import org.apache.camel.spring.CamelContextFactoryBean;
import org.apache.camel.spring.EndpointFactoryBean;
import org.apache.camel.spring.CamelBeanPostProcessor;
import static org.apache.camel.util.ObjectHelper.isNotNullOrBlank;
import org.apache.camel.builder.xml.XPathBuilder;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Set;
import java.util.HashSet;

public class CamelNamespaceHandler extends NamespaceHandlerSupport {
    protected CamelBeanDefinitionParser routesParser = new CamelBeanDefinitionParser(this);
    protected BeanDefinitionParser endpointParser = new BeanDefinitionParser(EndpointFactoryBean.class);
    protected BeanDefinitionParser beanPostProcessorParser = new BeanDefinitionParser(CamelBeanPostProcessor.class);
    protected Set<String> parserElementNames = new HashSet<String>();

    public void init() {
        registerParser("routes", routesParser);
        registerParser("routeBuilder", routesParser);
        registerParser("endpoint", endpointParser);

        registerParser("camelContext", new BeanDefinitionParser(CamelContextFactoryBean.class) {
            @Override
            protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
                super.doParse(element, parserContext, builder);

                String contextId = element.getAttribute("id");

                Element routes = element.getOwnerDocument().createElement("routes");
                // now lets move all the content there...
                NodeList list = element.getChildNodes();
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Node child = list.item(i);
                    if (child instanceof Element) {
                        Element childElement = (Element) child;
                        if (child.getLocalName().equals("beanPostProcessor")) {
                            String beanPostProcessorId = contextId + ":beanPostProcessor";
                            childElement.setAttribute("id", beanPostProcessorId);
                            BeanDefinition definition = beanPostProcessorParser.parse(childElement, parserContext);
                            definition.getPropertyValues().addPropertyValue("camelContext", new RuntimeBeanReference(contextId));
                        }
                        else {
                            element.removeChild(child);
                            routes.appendChild(child);
                        }
                    }
                }
                String routeId = contextId + ":routes";
                routes.setAttribute("id", routeId);

                BeanDefinition definition = routesParser.parse(routes, parserContext);
                definition.getPropertyValues().addPropertyValue("context", new RuntimeBeanReference(contextId));
                parserContext.registerComponent(new BeanComponentDefinition(definition, routeId));

                list = routes.getElementsByTagName("endpoint");
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Element node = (Element) list.item(i);
                    definition = endpointParser.parse(node, parserContext);
                    String id = node.getAttribute("id");
                    if (isNotNullOrBlank(id)) {
                        definition.getPropertyValues().addPropertyValue("context", new RuntimeBeanReference(contextId));
                        //definition.getPropertyValues().addPropertyValue("context", builder.getBeanDefinition());
                        parserContext.registerComponent(new BeanComponentDefinition(definition, id));
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


        // scripting expressions
        registerScriptParser("script", null);
        registerScriptParser("groovy", "groovy");
        registerScriptParser("ruby", "jruby");
        registerScriptParser("javaScript", "js");
        registerScriptParser("python", "python");
        registerScriptParser("php", "php");
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
}
