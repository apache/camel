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

public class XPathExpressionReifier extends SingleInputTypedExpressionReifier<XPathExpression> {

    public XPathExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (XPathExpression) definition);
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

    protected Object[] createProperties() {
        Object[] properties = new Object[11];
        properties[0] = asResultType();
        properties[1] = parseString(definition.getSource());
        properties[2] = definition.getDocumentType();
        properties[3] = asQName(parseString(definition.getResultQName()));
        properties[4] = parseBoolean(definition.getSaxon());
        properties[5] = definition.getXPathFactory();
        properties[6] = parseString(definition.getObjectModel());
        properties[7] = parseBoolean(definition.getThreadSafety());
        properties[8] = parseBoolean(definition.getPreCompile());
        properties[9] = parseBoolean(definition.getLogNamespaces());
        properties[10] = definition.getNamespaces();
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
            definition.setXPathFactory(mandatoryLookup(definition.getFactoryRef(), XPathFactory.class));
        }
    }

}
