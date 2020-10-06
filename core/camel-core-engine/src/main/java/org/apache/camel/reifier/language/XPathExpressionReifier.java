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
package org.apache.camel.reifier.language;

import java.util.HashMap;
import java.util.Map;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.support.CamelContextHelper;

public class XPathExpressionReifier extends ExpressionReifier<XPathExpression> {

    public XPathExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (XPathExpression) definition);
    }

    @Override
    protected Expression createExpression(Language language, String exp) {
        return language.createExpression(exp, createProperties());
    }

    @Override
    protected Predicate createPredicate(Language language, String exp) {
        return language.createPredicate(exp, createProperties());
    }

    @Override
    protected void configurePredicate(Predicate predicate) {
        configureNamespaceAware(predicate);
    }

    @Override
    protected void configureExpression(Expression expression) {
        configureNamespaceAware(expression);
    }

    protected void configureNamespaceAware(Object builder) {
        if (definition.getNamespaces() != null && builder instanceof NamespaceAware) {
            NamespaceAware namespaceAware = (NamespaceAware) builder;
            namespaceAware.setNamespaces(definition.getNamespaces());
        }
    }

    protected Map<String, Object> createProperties() {
        Map<String, Object> properties = new HashMap<>(9);
        properties.put("documentType", definition.getDocumentType());
        // resultType can either point to a QName or it can be a regular class that influence the qname
        // so we need this special logic to set resultQName and resultType accordingly
        Object qname = asQName(definition.getResultTypeName());
        properties.put("resultQName", qname);
        if (definition.getResultType() == null && qname == null && definition.getResultTypeName() != null) {
            properties.put("resultType", definition.getResultTypeName());
        } else {
            properties.put("resultType", definition.getResultType());
        }
        properties.put("useSaxon", parseBoolean(definition.getSaxon()));
        properties.put("xpathFactory", definition.getXPathFactory());
        properties.put("objectModelUri", parseString(definition.getObjectModel()));
        properties.put("threadSafety", parseBoolean(definition.getThreadSafety()));
        properties.put("logNamespaces", parseBoolean(definition.getLogNamespaces()));
        properties.put("headerName", parseString(definition.getHeaderName()));
        return properties;
    }

    private Object asQName(String resultTypeName) {
        if (resultTypeName == null) {
            return null;
        }
        if ("NUMBER".equalsIgnoreCase(resultTypeName)) {
            return XPathConstants.NUMBER;
        } else if ("STRING".equalsIgnoreCase(resultTypeName)) {
            return XPathConstants.STRING;
        } else if ("BOOLEAN".equalsIgnoreCase(resultTypeName)) {
            return XPathConstants.BOOLEAN;
        } else if ("NODESET".equalsIgnoreCase(resultTypeName)) {
            return XPathConstants.NODESET;
        } else if ("NODE".equalsIgnoreCase(resultTypeName)) {
            return XPathConstants.NODE;
        }
        return null;
    }

    @Override
    protected void configureLanguage(Language language) {
        if (definition.getDocumentType() == null && definition.getDocumentTypeName() != null) {
            try {
                Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(definition.getDocumentTypeName());
                definition.setDocumentType(clazz);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
        if (definition.getXPathFactory() == null && definition.getFactoryRef() != null) {
            definition.setXPathFactory(
                    CamelContextHelper.mandatoryLookupAndConvert(camelContext, definition.getFactoryRef(), XPathFactory.class));
        }
    }

}
