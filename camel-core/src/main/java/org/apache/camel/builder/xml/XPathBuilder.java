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
package org.apache.camel.builder.xml;

import static org.apache.camel.converter.ObjectConverter.toBoolean;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.Message;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import java.io.StringReader;

/**
 * Creates an XPath expression builder
 *
 * @version $Revision: 531854 $
 */
public class XPathBuilder<E extends Exchange> implements Expression<E>, Predicate<E> {
    private final String text;
    private XPathFactory xpathFactory;
    private Class documentType = Document.class;
    private QName resultType = null;
    private String objectModelUri = null;
    private DefaultNamespaceContext namespaceContext;
    private XPathFunctionResolver functionResolver;
    private XPathExpression expression;
    private MessageVariableResolver variableResolver = new MessageVariableResolver();

    public static XPathBuilder xpath(String text) {
        return new XPathBuilder(text);
    }

    public XPathBuilder(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "XPath: " + text;
    }

    public boolean matches(E exchange) {
        Object booleanResult = evaluateAs(exchange, XPathConstants.BOOLEAN);
        return toBoolean(booleanResult);
    }

    public void assertMatches(String text, E exchange) throws AssertionError {
        Object booleanResult = evaluateAs(exchange, XPathConstants.BOOLEAN);
        if (!toBoolean(booleanResult)) {
            throw new AssertionError(this + " failed on " + exchange + " as returned <" + booleanResult + ">");
        }
    }

    public Object evaluate(E exchange) {
        return evaluateAs(exchange, resultType);
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

    /**
     * Registers a variable (in the global namespace) which can be referred to from XPath expressions
     */
    public XPathBuilder<E> variable(String name, Object value) {
        variableResolver.addVariable(name, value);
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

    public XPathExpression getExpression() throws XPathFactoryConfigurationException, XPathExpressionException {
        if (expression == null) {
            expression = createXPathExpression();
        }
        return expression;
    }

    public void setNamespacesFromDom(Element node) {
        getNamespaceContext().setNamespacesFromDom(node);        
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    /**
     * Evaluates the expression as the given result type
     */
    protected synchronized Object evaluateAs(E exchange, QName resultType) {
        variableResolver.setExchange(exchange);
        try {
            Object document = getDocument(exchange);
            if (resultType != null) {
                if (document instanceof InputSource) {
                    InputSource inputSource = (InputSource) document;
                    return getExpression().evaluate(inputSource, resultType);
                }
                else {
                    return getExpression().evaluate(document, resultType);
                }
            }
            else {
                if (document instanceof InputSource) {
                    InputSource inputSource = (InputSource) document;
                    return getExpression().evaluate(inputSource);
                }
                else {
                    return getExpression().evaluate(document);
                }
            }
        }
        catch (XPathExpressionException e) {
            throw new InvalidXPathExpression(getText(), e);
        }
        catch (XPathFactoryConfigurationException e) {
            throw new InvalidXPathExpression(getText(), e);
        }
    }

    protected XPathExpression createXPathExpression() throws XPathExpressionException, XPathFactoryConfigurationException {
        XPath xPath = getXPathFactory().newXPath();

        // lets now clear any factory references to avoid keeping them around
        xpathFactory = null;

        xPath.setNamespaceContext(getNamespaceContext());
        xPath.setXPathVariableResolver(variableResolver);
        if (functionResolver != null) {
            xPath.setXPathFunctionResolver(functionResolver);
        }
        return xPath.compile(text);
    }

    /**
     * Strategy method to extract the document from the exchange
     */
    protected Object getDocument(E exchange) {
        Message in = exchange.getIn();
        Class type = getDocumentType();
        Object answer = null;
        if (type != null) {
            answer = in.getBody(type);
        }
        if (answer == null) {
            answer = in.getBody();
        }

        // lets try coerce some common types into something JAXP can deal with
        if (answer instanceof String) {
            answer = new InputSource(new StringReader(answer.toString()));
        }
        return answer;
    }


}
