/*
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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.spring.CamelRedeliveryPolicyFactoryBean;
import org.apache.camel.spring.ErrorHandlerType;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The DefinitionParser to deal with the ErrorHandler
 */
public class ErrorHandlerDefinitionParser extends BeanDefinitionParser {
    protected BeanDefinitionParser redeliveryPolicyParser = new RedeliveryPolicyDefinitionParser(CamelRedeliveryPolicyFactoryBean.class);
    
    public ErrorHandlerDefinitionParser() {
        // need to override the default
        super(null, false);
    }

    @Override
    protected Class<?> getBeanClass(Element element) {
        ErrorHandlerType type = ErrorHandlerType.DefaultErrorHandler;

        if (ObjectHelper.isNotEmpty(element.getAttribute("type"))) {
            type = ErrorHandlerType.valueOf(element.getAttribute("type"));
        }
        return type.getTypeAsClass();
    }
    
    @Override
    protected boolean isEligibleAttribute(String attributeName) {
        if (attributeName == null || ID_ATTRIBUTE.equals(attributeName)) {
            return false;
        }
        if (attributeName.equals("xmlns") || attributeName.startsWith("xmlns:")) {
            return false;
        }
        // CHECKSTYLE:OFF
        return !attributeName.equals("type")
                && !attributeName.equals("onRedeliveryRef")
                && !attributeName.equals("onRetryWhileRef")
                && !attributeName.equals("onPrepareFailureRef")
                && !attributeName.equals("onExceptionOccurredRef")
                && !attributeName.equals("redeliveryPolicyRef")
                && !attributeName.equals("transactionTemplateRef")
                && !attributeName.equals("transactionManagerRef");
        // CHECKSTYLE:ON
    }

    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        super.doParse(element, parserContext, builder);

        String id = element.getAttribute("id");

        ErrorHandlerType type = ErrorHandlerType.DefaultErrorHandler;
        if (ObjectHelper.isNotEmpty(element.getAttribute("type"))) {
            type = ErrorHandlerType.valueOf(element.getAttribute("type"));
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
                        // cannot have redeliveryPolicyRef attribute as well, only one is allowed
                        if (ObjectHelper.isNotEmpty(element.getAttribute("redeliveryPolicyRef"))) {
                            throw new IllegalArgumentException("Cannot set both redeliveryPolicyRef and redeliveryPolicy,"
                                    + " only one allowed, in error handler with id: " + id);
                        }
                        BeanDefinition redeliveryPolicyDefinition = redeliveryPolicyParser.parse(childElement, parserContext);
                        builder.addPropertyValue(localName, redeliveryPolicyDefinition);
                    }
                }
            }
            parserRefAttribute(element, "onRedeliveryRef", "onRedelivery", builder);
            parserRefAttribute(element, "onRetryWhileRef", "onRetryWhile", builder);
            parserRefAttribute(element, "onPrepareFailureRef", "onPrepareFailure", builder);
            parserRefAttribute(element, "onExceptionOccurredRef", "onExceptionOccurred", builder);
            parserRefAttribute(element, "redeliveryPolicyRef", "redeliveryPolicy", builder);
            if (type.equals(ErrorHandlerType.TransactionErrorHandler)) {
                parserRefAttribute(element, "transactionTemplateRef", "transactionTemplate", builder);
                parserRefAttribute(element, "transactionManagerRef", "transactionManager", builder);
            }
        }

        // validate attributes according to type

        String deadLetterUri = element.getAttribute("deadLetterUri");
        if (ObjectHelper.isNotEmpty(deadLetterUri) && !type.equals(ErrorHandlerType.DeadLetterChannel)) {
            throw new IllegalArgumentException("Attribute deadLetterUri can only be used if type is "
                    + ErrorHandlerType.DeadLetterChannel.name() + ", in error handler with id: " + id);
        }
        String deadLetterHandleNewException = element.getAttribute("deadLetterHandleNewException");
        if (ObjectHelper.isNotEmpty(deadLetterHandleNewException) && !type.equals(ErrorHandlerType.DeadLetterChannel)) {
            throw new IllegalArgumentException("Attribute deadLetterHandleNewException can only be used if type is "
                    + ErrorHandlerType.DeadLetterChannel.name() + ", in error handler with id: " + id);
        }
        String transactionTemplateRef = element.getAttribute("transactionTemplateRef");
        if (ObjectHelper.isNotEmpty(transactionTemplateRef) && !type.equals(ErrorHandlerType.TransactionErrorHandler)) {
            throw new IllegalArgumentException("Attribute transactionTemplateRef can only be used if type is "
                    + ErrorHandlerType.TransactionErrorHandler.name() + ", in error handler with id: " + id);
        }
        String transactionManagerRef = element.getAttribute("transactionManagerRef");
        if (ObjectHelper.isNotEmpty(transactionManagerRef) && !type.equals(ErrorHandlerType.TransactionErrorHandler)) {
            throw new IllegalArgumentException("Attribute transactionManagerRef can only be used if type is "
                    + ErrorHandlerType.TransactionErrorHandler.name() + ", in error handler with id: " + id);
        }
        String rollbackLoggingLevel = element.getAttribute("rollbackLoggingLevel");
        if (ObjectHelper.isNotEmpty(rollbackLoggingLevel) && (!type.equals(ErrorHandlerType.TransactionErrorHandler))) {
            throw new IllegalArgumentException("Attribute rollbackLoggingLevel can only be used if type is "
                    + ErrorHandlerType.TransactionErrorHandler.name() + ", in error handler with id: " + id);
        }
        String useOriginalMessage = element.getAttribute("useOriginalMessage");
        if (ObjectHelper.isNotEmpty(useOriginalMessage) && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw new IllegalArgumentException("Attribute useOriginalMessage is not supported by error handler type: "
                    + type.name() + ", in error handler with id: " + id);
        }
        String useOriginalBody = element.getAttribute("useOriginalBody");
        if (ObjectHelper.isNotEmpty(useOriginalBody) && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw new IllegalArgumentException("Attribute useOriginalBody is not supported by error handler type: "
                    + type.name() + ", in error handler with id: " + id);
        }
        String onRedeliveryRef = element.getAttribute("onRedeliveryRef");
        if (ObjectHelper.isNotEmpty(onRedeliveryRef) && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw new IllegalArgumentException("Attribute onRedeliveryRef is not supported by error handler type: "
                    + type.name() + ", in error handler with id: " + id);
        }
        String onExceptionOccurredRef = element.getAttribute("onExceptionOccurredRef");
        if (ObjectHelper.isNotEmpty(onExceptionOccurredRef) && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw new IllegalArgumentException("Attribute onExceptionOccurredRef is not supported by error handler type: "
                    + type.name() + ", in error handler with id: " + id);
        }
        String onPrepareFailureRef = element.getAttribute("onPrepareFailureRef");
        if (ObjectHelper.isNotEmpty(onPrepareFailureRef) && (type.equals(ErrorHandlerType.TransactionErrorHandler)
            || type.equals(ErrorHandlerType.NoErrorHandler))) {
            throw new IllegalArgumentException("Attribute onPrepareFailureRef is not supported by error handler type: "
                    + type.name() + ", in error handler with id: " + id);
        }
        String retryWhileRef = element.getAttribute("retryWhileRef");
        if (ObjectHelper.isNotEmpty(retryWhileRef) && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw new IllegalArgumentException("Attribute retryWhileRef is not supported by error handler type: "
                    + type.name() + ", in error handler with id: " + id);
        }
        String redeliveryPolicyRef = element.getAttribute("redeliveryPolicyRef");
        if (ObjectHelper.isNotEmpty(redeliveryPolicyRef) && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw new IllegalArgumentException("Attribute redeliveryPolicyRef is not supported by error handler type: "
                    + type.name() + ", in error handler with id: " + id);
        }
        String executorServiceRef = element.getAttribute("executorServiceRef");
        if (ObjectHelper.isNotEmpty(executorServiceRef) && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw new IllegalArgumentException("Attribute executorServiceRef is not supported by error handler type: "
                    + type.name() + ", in error handler with id: " + id);
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
    
    protected class RedeliveryPolicyDefinitionParser extends BeanDefinitionParser {

        public RedeliveryPolicyDefinitionParser(Class<?> type) {
            super(type, false);
        }

        @Override
        protected boolean shouldGenerateId() {
            return true;
        }
    }
    
}
