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
package org.apache.camel.builder.xml;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import javax.xml.xpath.XPathFunctionResolver;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.ExchangeHelper;

import static org.apache.camel.builder.xml.Namespaces.DEFAULT_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.IN_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.OUT_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.isMatchingNamespaceOrEmptyNamespace;
import static org.apache.camel.converter.ObjectConverter.toBoolean;

/**
 * Creates an XPath expression builder which creates a nodeset result by default.
 * If you want to evaluate a String expression then call {@link #stringResult()}
 *
 * @see XPathConstants#NODESET
 *
 * @version $Revision$
 */
public class XPathBuilder<E extends Exchange> implements Expression<E>, Predicate<E>, NamespaceAware {
    private final String text;
    private XPathFactory xpathFactory;
    private Class documentType = Document.class;
    // For some reason the default expression of "a/b" on a document such as
    // <a><b>1</b><b>2</b></a>
    // will evaluate as just "1" by default which is bizarre. So by default
    // lets assume XPath expressions result in nodesets.
    private Class resultType;
    private QName resultQName = XPathConstants.NODESET;
    private String objectModelUri;
    private DefaultNamespaceContext namespaceContext;
    private XPathFunctionResolver functionResolver;
    private XPathExpression expression;
    private MessageVariableResolver variableResolver = new MessageVariableResolver();
    private E exchange;
    private XPathFunction bodyFunction;
    private XPathFunction headerFunction;
    private XPathFunction outBodyFunction;
    private XPathFunction outHeaderFunction;

    public XPathBuilder(String text) {
        this.text = text;
    }

    public static XPathBuilder xpath(String text) {
        return new XPathBuilder(text);
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
        Object answer = evaluateAs(exchange, resultQName);
        if (resultType != null) {
            return ExchangeHelper.convertToType(exchange, resultType, answer);
        }
        return answer;
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> booleanResult() {
        resultQName = XPathConstants.BOOLEAN;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> nodeResult() {
        resultQName = XPathConstants.NODE;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> nodeSetResult() {
        resultQName = XPathConstants.NODESET;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> numberResult() {
        resultQName = XPathConstants.NUMBER;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> stringResult() {
        resultQName = XPathConstants.STRING;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder<E> resultType(Class resultType) {
        setResultType(resultType);
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
     * Sets the {@link XPathFunctionResolver} instance to use on these XPath
     * expressions
     *
     * @return the current builder
     */
    public XPathBuilder<E> functionResolver(XPathFunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
        return this;
    }

    /**
     * Registers the namespace prefix and URI with the builder so that the
     * prefix can be used in XPath expressions
     *
     * @param prefix is the namespace prefix that can be used in the XPath
     *                expressions
     * @param uri is the namespace URI to which the prefix refers
     * @return the current builder
     */
    public XPathBuilder<E> namespace(String prefix, String uri) {
        getNamespaceContext().add(prefix, uri);
        return this;
    }

    /**
     * Registers namespaces with the builder so that the registered
     * prefixes can be used in XPath expressions
     *
     * @param namespaces is namespaces object that should be used in the
     *                      XPath expression
     * @return the current builder
     */
    public XPathBuilder<E> namespaces(Namespaces namespaces) {
        namespaces.configure(this);
        return this;
    }

    /**
     * Registers a variable (in the global namespace) which can be referred to
     * from XPath expressions
     */
    public XPathBuilder<E> variable(String name, Object value) {
        variableResolver.addVariable(name, value);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------
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

    public QName getResultQName() {
        return resultQName;
    }

    public void setResultQName(QName resultQName) {
        this.resultQName = resultQName;
    }

    public DefaultNamespaceContext getNamespaceContext() {
        if (namespaceContext == null) {
            try {
                DefaultNamespaceContext defaultNamespaceContext = new DefaultNamespaceContext(getXPathFactory());
                populateDefaultNamespaces(defaultNamespaceContext);
                namespaceContext = defaultNamespaceContext;
            } catch (XPathFactoryConfigurationException e) {
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

    public XPathExpression getExpression() throws XPathFactoryConfigurationException,
        XPathExpressionException {
        if (expression == null) {
            expression = createXPathExpression();
        }
        return expression;
    }

    public void setNamespaces(Map<String, String> namespaces) {
        getNamespaceContext().setNamespaces(namespaces);
    }

    public XPathFunction getBodyFunction() {
        if (bodyFunction == null) {
            bodyFunction = new XPathFunction() {
                public Object evaluate(List list) throws XPathFunctionException {
                    if (exchange == null) {
                        return null;
                    }
                    return exchange.getIn().getBody();
                }
            };
        }
        return bodyFunction;
    }

    public void setBodyFunction(XPathFunction bodyFunction) {
        this.bodyFunction = bodyFunction;
    }

    public XPathFunction getHeaderFunction() {
        if (headerFunction == null) {
            headerFunction = new XPathFunction() {
                public Object evaluate(List list) throws XPathFunctionException {
                    if (exchange != null && !list.isEmpty()) {
                        Object value = list.get(0);
                        if (value != null) {
                            return exchange.getIn().getHeader(value.toString());
                        }
                    }
                    return null;
                }
            };
        }
        return headerFunction;
    }

    public void setHeaderFunction(XPathFunction headerFunction) {
        this.headerFunction = headerFunction;
    }

    public XPathFunction getOutBodyFunction() {
        if (outBodyFunction == null) {
            outBodyFunction = new XPathFunction() {
                public Object evaluate(List list) throws XPathFunctionException {
                    if (exchange != null) {
                        Message out = exchange.getOut(false);
                        if (out != null) {
                            return out.getBody();
                        }
                    }
                    return null;
                }
            };
        }
        return outBodyFunction;
    }

    public void setOutBodyFunction(XPathFunction outBodyFunction) {
        this.outBodyFunction = outBodyFunction;
    }

    public XPathFunction getOutHeaderFunction() {
        if (outHeaderFunction == null) {
            outHeaderFunction = new XPathFunction() {
                public Object evaluate(List list) throws XPathFunctionException {
                    if (exchange != null && !list.isEmpty()) {
                        Object value = list.get(0);
                        if (value != null) {
                            return exchange.getOut().getHeader(value.toString());
                        }
                    }
                    return null;
                }
            };
        }
        return outHeaderFunction;
    }

    public void setOutHeaderFunction(XPathFunction outHeaderFunction) {
        this.outHeaderFunction = outHeaderFunction;
    }

    public Class getResultType() {
        return resultType;
    }

    public void setResultType(Class resultType) {
        this.resultType = resultType;
        if (Number.class.isAssignableFrom(resultType)) {
            numberResult();
        } else if (String.class.isAssignableFrom(resultType)) {
            stringResult();
        } else if (Boolean.class.isAssignableFrom(resultType)) {
            booleanResult();
        } else if (Node.class.isAssignableFrom(resultType)) {
            nodeResult();
        } else if (NodeList.class.isAssignableFrom(resultType)) {
            nodeSetResult();
        }
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Evaluates the expression as the given result type
     */
    protected synchronized Object evaluateAs(E exchange, QName resultQName) {
        this.exchange = exchange;
        variableResolver.setExchange(exchange);
        try {
            Object document = getDocument(exchange);
            if (resultQName != null) {
                if (document instanceof InputSource) {
                    InputSource inputSource = (InputSource)document;
                    return getExpression().evaluate(inputSource, resultQName);
                } else if (document instanceof DOMSource) {
                    DOMSource source = (DOMSource) document;
                    return getExpression().evaluate(source.getNode(), resultQName);
                } else {
                    return getExpression().evaluate(document, resultQName);
                }
            } else {
                if (document instanceof InputSource) {
                    InputSource inputSource = (InputSource)document;
                    return getExpression().evaluate(inputSource);
                } else if (document instanceof DOMSource) {
                    DOMSource source = (DOMSource)document;
                    return getExpression().evaluate(source.getNode());
                } else {
                    return getExpression().evaluate(document);
                }
            }
        } catch (XPathExpressionException e) {
            throw new InvalidXPathExpression(getText(), e);
        } catch (XPathFactoryConfigurationException e) {
            throw new InvalidXPathExpression(getText(), e);
        }
    }

    protected XPathExpression createXPathExpression() throws XPathExpressionException,
        XPathFactoryConfigurationException {
        XPath xPath = getXPathFactory().newXPath();

        // lets now clear any factory references to avoid keeping them around
        xpathFactory = null;

        xPath.setNamespaceContext(getNamespaceContext());

        xPath.setXPathVariableResolver(variableResolver);

        XPathFunctionResolver parentResolver = getFunctionResolver();
        if (parentResolver == null) {
            parentResolver = xPath.getXPathFunctionResolver();
        }
        xPath.setXPathFunctionResolver(createDefaultFunctionResolver(parentResolver));
        return xPath.compile(text);
    }

    /**
     * Lets populate a number of standard prefixes if they are not already there
     */
    protected void populateDefaultNamespaces(DefaultNamespaceContext context) {
        setNamespaceIfNotPresent(context, "in", IN_NAMESPACE);
        setNamespaceIfNotPresent(context, "out", OUT_NAMESPACE);
        setNamespaceIfNotPresent(context, "env", Namespaces.ENVIRONMENT_VARIABLES);
        setNamespaceIfNotPresent(context, "system", Namespaces.SYSTEM_PROPERTIES_NAMESPACE);
    }

    protected void setNamespaceIfNotPresent(DefaultNamespaceContext context, String prefix, String uri) {
        if (context != null) {
            String current = context.getNamespaceURI(prefix);
            if (current == null) {
                context.add(prefix, uri);
            }
        }
    }

    protected XPathFunctionResolver createDefaultFunctionResolver(final XPathFunctionResolver parent) {
        return new XPathFunctionResolver() {
            public XPathFunction resolveFunction(QName qName, int argumentCount) {
                XPathFunction answer = null;
                if (parent != null) {
                    answer = parent.resolveFunction(qName, argumentCount);
                }
                if (answer == null) {
                    if (isMatchingNamespaceOrEmptyNamespace(qName.getNamespaceURI(), IN_NAMESPACE)
                        || isMatchingNamespaceOrEmptyNamespace(qName.getNamespaceURI(), DEFAULT_NAMESPACE)) {
                        String localPart = qName.getLocalPart();
                        if (localPart.equals("body") && argumentCount == 0) {
                            return getBodyFunction();
                        }
                        if (localPart.equals("header") && argumentCount == 1) {
                            return getHeaderFunction();
                        }
                    }
                    if (isMatchingNamespaceOrEmptyNamespace(qName.getNamespaceURI(), OUT_NAMESPACE)) {
                        String localPart = qName.getLocalPart();
                        if (localPart.equals("body") && argumentCount == 0) {
                            return getOutBodyFunction();
                        }
                        if (localPart.equals("header") && argumentCount == 1) {
                            return getOutHeaderFunction();
                        }
                    }
                    if (isMatchingNamespaceOrEmptyNamespace(qName.getNamespaceURI(), DEFAULT_NAMESPACE)) {
                        String localPart = qName.getLocalPart();
                        if (localPart.equals("out-body") && argumentCount == 0) {
                            return getOutBodyFunction();
                        }
                        if (localPart.equals("out-header") && argumentCount == 1) {
                            return getOutHeaderFunction();
                        }
                    }
                }
                return answer;
            }
        };
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
