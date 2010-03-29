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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.LoggingErrorHandlerBuilder;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.spring.CamelEndpointFactoryBean;
import org.apache.camel.spring.ErrorHandlerType;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The DefinitionParser to deal with the ErrorHandler
 */
public class ErrorHandlerDefinitionParser extends BeanDefinitionParser {
    private static final Log LOG = LogFactory.getLog(ErrorHandlerDefinitionParser.class);
    protected BeanDefinitionParser redeliveryPolicyParser = new RedeliveryPolicyDefinitionParser(RedeliveryPolicy.class);
    
    public ErrorHandlerDefinitionParser() {
        // Need to override the default 
        super(null);
        
    }

    protected Class getBeanClass(Element element) {
        ErrorHandlerType type = ErrorHandlerType.DefaultErrorHandler;
        
        if (ObjectHelper.isNotEmpty(element.getAttribute("type"))) {
            type = ErrorHandlerType.valueOf(element.getAttribute("type"));
        }
        Class clazz = null;
        if (type.equals(ErrorHandlerType.NoErrorHandler)) {
            clazz = NoErrorHandlerBuilder.class;
        }
        if (type.equals(ErrorHandlerType.DeadLetterChannel)) {
            clazz = DeadLetterChannelBuilder.class;
            
        }
        if (type.equals(ErrorHandlerType.LoggingErrorHandler)) {
            clazz = LoggingErrorHandlerBuilder.class;
        }
        if (type.equals(ErrorHandlerType.DefaultErrorHandler)) {
            clazz = DefaultErrorHandlerBuilder.class;
        }
        if (type.equals(ErrorHandlerType.TransactionErrorHandler)) {
            clazz = TransactionErrorHandlerBuilder.class;
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Cannot find the ErrorHandle with type " + type);
        }
        return clazz;
    }
    
    protected boolean isEligibleAttribute(String attributeName) {
        return attributeName != null && !ID_ATTRIBUTE.equals(attributeName)
                && !attributeName.equals("xmlns") && !attributeName.startsWith("xmlns:")
                && !attributeName.equals("type") && !attributeName.equals("onRedeliveryRef")
                && !attributeName.equals("transactionTemplateRef");
    }
   
    
    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        super.doParse(element, parserContext, builder);
        ErrorHandlerType type = ErrorHandlerType.DefaultErrorHandler;
        if (ObjectHelper.isNotEmpty(element.getAttribute("type"))) {
            type = ErrorHandlerType.valueOf(element.getAttribute("type"));
        }
        if (type.equals(ErrorHandlerType.NoErrorHandler) || type.equals(ErrorHandlerType.LoggingErrorHandler)) {
            // don't need to parser other stuff
            return;
        }
        if (type.equals(ErrorHandlerType.DefaultErrorHandler) 
            || type.equals(ErrorHandlerType.DeadLetterChannel) 
            || type.equals(ErrorHandlerType.TransactionErrorHandler)) {
            NodeList list = element.getChildNodes();
            int size = list.getLength();
            for (int i = 0; i < size; i++) {
                Node child = list.item(i);
                if (child instanceof Element) {
                    Element childElement = (Element)child;
                    String localName = child.getLocalName();
                    // set the redeliveryPolicy
                    if (localName.equals("redeliveryPolicy")) {
                        BeanDefinition redeliveryPolicyDefinition = redeliveryPolicyParser.parse(childElement, parserContext);
                        builder.addPropertyValue(localName, redeliveryPolicyDefinition);
                    }
                }
            }
            // deal with onRedeliveryRef
            parserRefAttribute(element, "onRedeliveryRef", "onRedelivery", builder);
        }
        if (type.equals(ErrorHandlerType.TransactionErrorHandler)) {
            // deal with transactionTemplateRef
            parserRefAttribute(element, "transactionTemplateRef", "transactionTemplate", builder);
        }
    }

    private void parserRefAttribute(Element element, String attributeName, String propertyName, BeanDefinitionBuilder builder) {
        NamedNodeMap attributes = element.getAttributes();
        for (int x = 0; x < attributes.getLength(); x++) {
            Attr attribute = (Attr) attributes.item(x);
            String name = attribute.getLocalName();
            if (name.equals(attributeName)) {
                Assert.state(StringUtils.hasText(propertyName),
                        "Illegal property name returned from 'extractPropertyName(String)': cannot be null or empty.");
                builder.addPropertyReference(propertyName, attribute.getValue());
            }
        }
    }
    
    class RedeliveryPolicyDefinitionParser extends BeanDefinitionParser {
        public RedeliveryPolicyDefinitionParser(Class type) {
            super(type);
        }

        protected boolean shouldGenerateId() {
            return true;
        }
    }
    
}
