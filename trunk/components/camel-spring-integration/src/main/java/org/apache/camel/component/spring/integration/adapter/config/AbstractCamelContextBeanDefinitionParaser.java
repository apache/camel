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
package org.apache.camel.component.spring.integration.adapter.config;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;

/**
 * This BeanDefinition parser help to inject the camel context into the beans
 *
 * @version 
 */
public class AbstractCamelContextBeanDefinitionParaser extends AbstractSingleBeanDefinitionParser {
    private static final String DEFAULT_CAMEL_CONTEXT_NAME = "camelContext";

    private String getContextId(String contextId) {
        if (ObjectHelper.isEmpty(contextId)) {
            //Set the contextId default value here
            return DEFAULT_CAMEL_CONTEXT_NAME;
        } else {
            return contextId;
        }
    }

    protected void mapToProperty(BeanDefinitionBuilder bean, String propertyName, String val) {
        if (ID_ATTRIBUTE.equals(propertyName)) {
            return;
        }

        if (StringUtils.hasText(val)) {
            if (val.startsWith("#")) {
                bean.addPropertyReference(propertyName, val.substring(1));
            } else {
                bean.addPropertyValue(propertyName, val);
            }
        }
    }

    protected void wireCamelContext(BeanDefinitionBuilder bean, String camelContextId) {
        bean.addPropertyReference("camelContext", camelContextId);
    }

    protected void parseAttributes(Element element, ParserContext ctx, BeanDefinitionBuilder bean) {
        NamedNodeMap atts = element.getAttributes();

        for (int i = 0; i < atts.getLength(); i++) {
            Attr node = (Attr) atts.item(i);
            String val = node.getValue();
            String name = node.getLocalName();

            if (name.equals("requestChannel") || name.equals("replyChannel")) {
                bean.addPropertyReference(name, val);
            } else {
                mapToProperty(bean, name, val);
            }
        }
    }

    protected void parseCamelContext(Element element, ParserContext ctx, BeanDefinitionBuilder bean) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                String name = n.getLocalName();
                if ("camelContext".equals(name)) {
                    // Parser the camel context
                    BeanDefinition bd = ctx.getDelegate().parseCustomElement((Element)n);
                    // Get the inner camel context id
                    String contextId = (String)bd.getPropertyValues().getPropertyValue("id").getValue();
                    wireCamelContext(bean, getContextId(contextId));
                } else if ("camelContextRef".equals(name)) {
                    String contextId = n.getTextContent();
                    wireCamelContext(bean, getContextId(contextId));
                }
            }
        }
    }

    protected void doParse(Element element, ParserContext ctx, BeanDefinitionBuilder bean) {
        parseAttributes(element, ctx, bean);
        parseCamelContext(element, ctx, bean);

    }

}
