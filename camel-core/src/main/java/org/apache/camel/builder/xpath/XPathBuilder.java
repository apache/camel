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
package org.apache.camel.builder.xpath;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.Provider;
import org.apache.camel.Predicate;
import org.apache.camel.builder.PredicateFactory;
import org.apache.camel.builder.ExpressionFactory;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFunctionResolver;

/**
 * Creates an XPath expression builder
 *
 * @version $Revision$
 */
public class XPathBuilder<E extends Exchange> implements ExpressionFactory<E>, PredicateFactory<E> {
    private final String text;
    private XPathFactory xpathFactory;
    private Class documentType = Document.class;
    private QName resultType = null;
    private String objectModelUri = null;
    private DefaultNamespaceContext namespaceContext;
    private XPathFunctionResolver functionResolver;

    public static XPathBuilder xpath(String text) {
        return new XPathBuilder(text);
    }

    public XPathBuilder(String text) {
        this.text = text;
    }

    public Expression<E> createExpression() {
        return createExchangeXPathExpression();
    }

    public Predicate<E> createPredicate() {
        return createExchangeXPathExpression();
    }
    

    // Builder methods
    //-------------------------------------------------------------------------

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> booleanResult() {
        resultType = XPathConstants.BOOLEAN;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> nodeResult() {
        resultType = XPathConstants.NODE;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> nodeSetResult() {
        resultType = XPathConstants.NODESET;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> numberResult() {
        resultType = XPathConstants.NUMBER;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> stringResult() {
        resultType = XPathConstants.STRING;
        return this;
    }

    /**
     * Sets the object model URI to use
     *
     * @return the current builder
     */
    public XPathBuilder<E> objectModel(String uri) {
        this.objectModelUri = uri;
        return this;
    }

    /**
     * Sets the {@link XPathFunctionResolver} instance to use on these XPath expressions
     *
     * @return the current builder
     */
    public XPathBuilder<E> functionResolver(XPathFunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
        return this;
    }

    /**
     * Registers the namespace prefix and URI with the builder so that the prefix can be used in XPath expressions
     *
     * @param prefix is the namespace prefix that can be used in the XPath expressions
     * @param uri is the namespace URI to which the prefix refers
     * @return the current builder
     */
    public XPathBuilder<E> namespace(String prefix, String uri) {
        getNamespaceContext().add(prefix, uri);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    public XPathFactory getXPathFactory() throws XPathFactoryConfigurationException {
        if (xpathFactory == null) {
            if (objectModelUri != null) {
                xpathFactory = XPathFactory.newInstance(objectModelUri);
            }
            xpathFactory = XPathFactory.newInstance();
        }
        return xpathFactory;
    }

    public void setXPathFactory(XPathFactory xpathFactory) {
        this.xpathFactory = xpathFactory;
    }

    public Class getDocumentType() {
        return documentType;
    }

    public void setDocumentType(Class documentType) {
        this.documentType = documentType;
    }

    public String getText() {
        return text;
    }

    public QName getResultType() {
        return resultType;
    }

    public DefaultNamespaceContext getNamespaceContext() {
        if (namespaceContext == null) {
            try {
                namespaceContext = new DefaultNamespaceContext(getXPathFactory());
            }
            catch (XPathFactoryConfigurationException e) {
                throw new RuntimeExpressionException(e);
            }
        }
        return namespaceContext;
    }

    public void setNamespaceContext(DefaultNamespaceContext namespaceContext) {
        this.namespaceContext = namespaceContext;
    }

    public XPathFunctionResolver getFunctionResolver() {
        return functionResolver;
    }

    public void setFunctionResolver(XPathFunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected ExchangeXPathExpression<E> createExchangeXPathExpression() {
        try {
            MessageVariableResolver variableResolver = new MessageVariableResolver();
            XPathExpression expression = createXPathExpression(variableResolver);
            return new ExchangeXPathExpression<E>(this, expression, variableResolver);
        }
        catch (XPathException e) {
            throw new InvalidXPathExpression(text, e);
        }
    }

    protected XPathExpression createXPathExpression(MessageVariableResolver variableResolver) throws XPathExpressionException, XPathFactoryConfigurationException {
        XPath xPath = getXPathFactory().newXPath();
        xPath.setNamespaceContext(getNamespaceContext());
        xPath.setXPathVariableResolver(variableResolver);
        if (functionResolver != null) {
            xPath.setXPathFunctionResolver(functionResolver);
        }
        return xPath.compile(text);
    }

}
