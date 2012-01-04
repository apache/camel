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

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.Service;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.bean.BeanInvocation;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.builder.xml.Namespaces.DEFAULT_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.FUNCTION_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.IN_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.OUT_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.isMatchingNamespaceOrEmptyNamespace;

/**
 * Creates an XPath expression builder which creates a nodeset result by default.
 * If you want to evaluate a String expression then call {@link #stringResult()}
 * <p/>
 * An XPath object is not thread-safe and not reentrant. In other words, it is the application's responsibility to make
 * sure that one XPath object is not used from more than one thread at any given time, and while the evaluate method
 * is invoked, applications may not recursively call the evaluate method.
 * <p/>
 * This implementation is thread safe by using thread locals and pooling to allow concurrency
 *
 * @see XPathConstants#NODESET
 */
public class XPathBuilder implements Expression, Predicate, NamespaceAware, Service {
    private static final transient Logger LOG = LoggerFactory.getLogger(XPathBuilder.class);
    private static final String SAXON_OBJECT_MODEL_URI = "http://saxon.sf.net/jaxp/xpath/om";
    private static final String OBTAIN_ALL_NS_XPATH = "//*/namespace::*";

    private static XPathFactory defaultXPathFactory;

    private final Queue<XPathExpression> pool = new ConcurrentLinkedQueue<XPathExpression>();
    private final Queue<XPathExpression> poolTraceNamespaces = new ConcurrentLinkedQueue<XPathExpression>();
    private final String text;
    private final ThreadLocal<MessageVariableResolver> variableResolver = new ThreadLocal<MessageVariableResolver>();
    private final ThreadLocal<Exchange> exchange = new ThreadLocal<Exchange>();
    private XPathFactory xpathFactory;
    private Class<?> documentType = Document.class;
    // For some reason the default expression of "a/b" on a document such as
    // <a><b>1</b><b>2</b></a>
    // will evaluate as just "1" by default which is bizarre. So by default
    // let's assume XPath expressions result in nodesets.
    private Class<?> resultType;
    private QName resultQName = XPathConstants.NODESET;
    private String objectModelUri;
    private DefaultNamespaceContext namespaceContext;
    private boolean traceNamespaces;
    private XPathFunctionResolver functionResolver;
    private XPathFunction bodyFunction;
    private XPathFunction headerFunction;
    private XPathFunction outBodyFunction;
    private XPathFunction outHeaderFunction;
    private XPathFunction propertiesFunction;
    private XPathFunction simpleFunction;

    public XPathBuilder(String text) {
        this.text = text;
    }

    public static XPathBuilder xpath(String text) {
        return new XPathBuilder(text);
    }

    public static XPathBuilder xpath(String text, Class<?> resultType) {
        XPathBuilder builder = new XPathBuilder(text);
        builder.setResultType(resultType);
        return builder;
    }

    @Override
    public String toString() {
        return "XPath: " + text;
    }

    public boolean matches(Exchange exchange) {
        // add on completion so the thread locals is removed when exchange is done
        exchange.addOnCompletion(new XPathBuilderOnCompletion());

        Object booleanResult = evaluateAs(exchange, XPathConstants.BOOLEAN);
        return exchange.getContext().getTypeConverter().convertTo(Boolean.class, booleanResult);
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        // add on completion so the thread locals is removed when exchange is done
        exchange.addOnCompletion(new XPathBuilderOnCompletion());

        Object result = evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, result);
    }

    /**
     * Matches the given xpath using the provided body.
     *
     * @param context the camel context
     * @param body    the body
     * @return <tt>true</tt> if matches, <tt>false</tt> otherwise
     */
    public boolean matches(CamelContext context, Object body) {
        ObjectHelper.notNull(context, "CamelContext");

        // create a dummy Exchange to use during matching
        Exchange dummy = new DefaultExchange(context);
        dummy.getIn().setBody(body);

        boolean answer = matches(dummy);

        // remove the dummy from the thread local after usage
        variableResolver.remove();
        exchange.remove();
        return answer;
    }

    /**
     * Evaluates the given xpath using the provided body.
     *
     * @param context the camel context
     * @param body    the body
     * @param type    the type to return
     * @return result of the evaluation
     */
    public <T> T evaluate(CamelContext context, Object body, Class<T> type) {
        ObjectHelper.notNull(context, "CamelContext");

        // create a dummy Exchange to use during evaluation
        Exchange dummy = new DefaultExchange(context);
        dummy.getIn().setBody(body);

        T answer = evaluate(dummy, type);

        // remove the dummy from the thread local after usage
        variableResolver.remove();
        exchange.remove();
        return answer;
    }

    /**
     * Evaluates the given xpath using the provided body as a String return type.
     *
     * @param context the camel context
     * @param body    the body
     * @return result of the evaluation
     */
    public String evaluate(CamelContext context, Object body) {
        ObjectHelper.notNull(context, "CamelContext");

        // create a dummy Exchange to use during evaluation
        Exchange dummy = new DefaultExchange(context);
        dummy.getIn().setBody(body);

        setResultQName(XPathConstants.STRING);
        String answer = evaluate(dummy, String.class);

        // remove the dummy from the thread local after usage
        variableResolver.remove();
        exchange.remove();
        return answer;
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder booleanResult() {
        resultQName = XPathConstants.BOOLEAN;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder nodeResult() {
        resultQName = XPathConstants.NODE;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder nodeSetResult() {
        resultQName = XPathConstants.NODESET;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder numberResult() {
        resultQName = XPathConstants.NUMBER;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder stringResult() {
        resultQName = XPathConstants.STRING;
        return this;
    }

    /**
     * Sets the expression result type to boolean
     *
     * @return the current builder
     */
    public XPathBuilder resultType(Class<?> resultType) {
        setResultType(resultType);
        return this;
    }

    /**
     * Sets the object model URI to use
     *
     * @return the current builder
     */
    public XPathBuilder objectModel(String uri) {
        // TODO: Careful! Setting the Object Model URI this way will set the *Default* XPath Factory, which since is a static field,
        // will set the XPath Factory system-wide. Decide what to do, as changing this behaviour can break compatibility. Provided the setObjectModel which changes
        // this instance's XPath Factory rather than the static field
        this.objectModelUri = uri;
        return this;
    }

    /**
     * Configures to use Saxon as the XPathFactory which allows you to use XPath 2.0 functions
     * which may not be part of the build in JDK XPath parser.
     *
     * @return the current builder
     */
    public XPathBuilder saxon() {
        this.objectModelUri = SAXON_OBJECT_MODEL_URI;
        return this;
    }

    /**
     * Sets the {@link XPathFunctionResolver} instance to use on these XPath
     * expressions
     *
     * @return the current builder
     */
    public XPathBuilder functionResolver(XPathFunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
        return this;
    }

    /**
     * Registers the namespace prefix and URI with the builder so that the
     * prefix can be used in XPath expressions
     *
     * @param prefix is the namespace prefix that can be used in the XPath
     *               expressions
     * @param uri    is the namespace URI to which the prefix refers
     * @return the current builder
     */
    public XPathBuilder namespace(String prefix, String uri) {
        getNamespaceContext().add(prefix, uri);
        return this;
    }

    /**
     * Registers namespaces with the builder so that the registered
     * prefixes can be used in XPath expressions
     *
     * @param namespaces is namespaces object that should be used in the
     *                   XPath expression
     * @return the current builder
     */
    public XPathBuilder namespaces(Namespaces namespaces) {
        namespaces.configure(this);
        return this;
    }

    /**
     * Registers a variable (in the global namespace) which can be referred to
     * from XPath expressions
     *
     * @param name  name of variable
     * @param value value of variable
     * @return the current builder
     */
    public XPathBuilder variable(String name, Object value) {
        getVariableResolver().addVariable(name, value);
        return this;
    }

    /**
     * Configures the document type to use.
     * <p/>
     * The document type controls which kind of Class Camel should convert the payload
     * to before doing the xpath evaluation.
     * <p/>
     * For example you can set it to {@link InputSource} to use SAX streams.
     * By default Camel uses {@link Document} as the type.
     *
     * @param documentType the document type
     * @return the current builder
     */
    public XPathBuilder documentType(Class<?> documentType) {
        setDocumentType(documentType);
        return this;
    }

    /**
     * Configures to use the provided XPath factory.
     * <p/>
     * Can be used to use Saxon instead of the build in factory from the JDK.
     *
     * @param xpathFactory the xpath factory to use
     * @return the current builder.
     */
    public XPathBuilder factory(XPathFactory xpathFactory) {
        setXPathFactory(xpathFactory);
        return this;
    }

    /**
     * Activates trace logging of all discovered namespaces in the message - to simplify debugging namespace-related issues
     * <p/>
     * Namespaces are printed in Hashmap style <code>{xmlns:prefix=[namespaceURI], xmlns:prefix=[namespaceURI]}</code>.
     * <p/>
     * The implicit XML namespace is omitted (http://www.w3.org/XML/1998/namespace).
     * XML allows for namespace prefixes to be redefined/overridden due to hierarchical scoping, i.e. prefix abc can be mapped to http://abc.com,
     * and deeper in the document it can be mapped to http://def.com. When two prefixes are detected which are equal but are mapped to different
     * namespace URIs, Camel will show all namespaces URIs it is mapped to in an array-style.
     * <p/>
     * This feature is disabled by default.
     *
     * @return the current builder.
     */
    public XPathBuilder traceNamespaces() {
        setTraceNamespaces(true);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------
    public XPathFactory getXPathFactory() throws XPathFactoryConfigurationException {
        if (xpathFactory != null) {
            return xpathFactory;
        }

        if (objectModelUri != null) {
            xpathFactory = XPathFactory.newInstance(objectModelUri);
            LOG.info("Using objectModelUri " + objectModelUri + " when created XPathFactory {}", defaultXPathFactory);
            return xpathFactory;
        }

        if (defaultXPathFactory == null) {
            initDefaultXPathFactory();
        }
        return defaultXPathFactory;
    }

    public void setXPathFactory(XPathFactory xpathFactory) {
        this.xpathFactory = xpathFactory;
    }

    public Class<?> getDocumentType() {
        return documentType;
    }

    public void setDocumentType(Class<?> documentType) {
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
                    return exchange.get().getIn().getBody();
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
                            String text = exchange.get().getContext().getTypeConverter().convertTo(String.class, value);
                            return exchange.get().getIn().getHeader(text);
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
                    if (exchange.get() != null && exchange.get().hasOut()) {
                        return exchange.get().getOut().getBody();
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
                    if (exchange.get() != null && !list.isEmpty()) {
                        Object value = list.get(0);
                        if (value != null) {
                            String text = exchange.get().getContext().getTypeConverter().convertTo(String.class, value);
                            return exchange.get().getOut().getHeader(text);
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

    public XPathFunction getPropertiesFunction() {
        if (propertiesFunction == null) {
            propertiesFunction = new XPathFunction() {
                public Object evaluate(List list) throws XPathFunctionException {
                    if (exchange != null && !list.isEmpty()) {
                        Object value = list.get(0);
                        if (value != null) {
                            String text = exchange.get().getContext().getTypeConverter().convertTo(String.class, value);
                            try {
                                // use the property placeholder resolver to lookup the property for us
                                Object answer = exchange.get().getContext().resolvePropertyPlaceholders("{{" + text + "}}");
                                return answer;
                            } catch (Exception e) {
                                throw new XPathFunctionException(e);
                            }
                        }
                    }
                    return null;
                }
            };
        }
        return propertiesFunction;
    }

    public void setPropertiesFunction(XPathFunction propertiesFunction) {
        this.propertiesFunction = propertiesFunction;
    }

    public XPathFunction getSimpleFunction() {
        if (simpleFunction == null) {
            simpleFunction = new XPathFunction() {
                public Object evaluate(List list) throws XPathFunctionException {
                    if (exchange != null && !list.isEmpty()) {
                        Object value = list.get(0);
                        if (value != null) {
                            String text = exchange.get().getContext().getTypeConverter().convertTo(String.class, value);
                            Language simple = exchange.get().getContext().resolveLanguage("simple");
                            Expression exp = simple.createExpression(text);
                            Object answer = exp.evaluate(exchange.get(), Object.class);
                            return answer;
                        }
                    }
                    return null;
                }
            };
        }
        return simpleFunction;
    }

    public void setSimpleFunction(XPathFunction simpleFunction) {
        this.simpleFunction = simpleFunction;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
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

    public void setTraceNamespaces(boolean traceNamespaces) {
        this.traceNamespaces = traceNamespaces;
    }

    public boolean isTraceNamespaces() {
        return traceNamespaces;
    }

    public String getObjectModelUri() {
        return objectModelUri;
    }

    /**
     * Enables Saxon on this particular XPath expression, as {@link #saxon()} sets the default static XPathFactory which may have already been initialised
     * by previous XPath expressions
     */
    public void enableSaxon() {
        this.setObjectModelUri(SAXON_OBJECT_MODEL_URI);
    }

    public void setObjectModelUri(String objectModelUri) {
        this.objectModelUri = objectModelUri;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected Object evaluate(Exchange exchange) {
        Object answer = evaluateAs(exchange, resultQName);
        if (resultType != null) {
            return ExchangeHelper.convertToType(exchange, resultType, answer);
        }
        return answer;
    }

    /**
     * Evaluates the expression as the given result type
     */
    protected Object evaluateAs(Exchange exchange, QName resultQName) {
        // pool a pre compiled expression from pool
        XPathExpression xpathExpression = pool.poll();
        if (xpathExpression == null) {
            LOG.trace("Creating new XPathExpression as none was available from pool");
            // no avail in pool then create one
            try {
                xpathExpression = createXPathExpression();
            } catch (XPathExpressionException e) {
                throw new InvalidXPathExpression(getText(), e);
            } catch (Exception e) {
                throw new RuntimeExpressionException("Cannot create xpath expression", e);
            }
        } else {
            LOG.trace("Acquired XPathExpression from pool");
        }
        try {
            if (traceNamespaces && LOG.isTraceEnabled()) {
                traceNamespaces(exchange);
            }
            return doInEvaluateAs(xpathExpression, exchange, resultQName);
        } finally {
            // release it back to the pool
            pool.add(xpathExpression);
            LOG.trace("Released XPathExpression back to pool");
        }
    }

    private void traceNamespaces(Exchange exchange) {
        InputStream is = null;
        NodeList answer = null;
        XPathExpression xpathExpression = null;

        try {
            xpathExpression = poolTraceNamespaces.poll();
            if (xpathExpression == null) {
                xpathExpression = createTraceNamespaceExpression();
            }

            // prepare the input
            Object document;
            if (isInputStreamNeeded(exchange)) {
                is = exchange.getIn().getBody(InputStream.class);
                document = getDocument(exchange, is);
            } else {
                Object body = exchange.getIn().getBody();
                document = getDocument(exchange, body);
            }
            // fetch all namespaces
            if (document instanceof InputSource) {
                InputSource inputSource = (InputSource) document;
                answer = (NodeList) xpathExpression.evaluate(inputSource, XPathConstants.NODESET);
            } else if (document instanceof DOMSource) {
                DOMSource source = (DOMSource) document;
                answer = (NodeList) xpathExpression.evaluate(source.getNode(), XPathConstants.NODESET);
            } else {
                answer = (NodeList) xpathExpression.evaluate(document, XPathConstants.NODESET);
            }
        } catch (Exception e) {
            LOG.trace("Unable to trace discovered namespaces in XPath expression", e);
        } finally {
            // IOHelper can handle if is is null
            IOHelper.close(is);
            poolTraceNamespaces.add(xpathExpression);
        }

        if (answer != null) {
            logDiscoveredNamespaces(answer);
        }
    }

    private void logDiscoveredNamespaces(NodeList namespaces) {
        HashMap<String, HashSet<String>> map = new LinkedHashMap<String, HashSet<String>>();
        for (int i = 0; i < namespaces.getLength(); i++) {
            Node n = namespaces.item(i);
            if (n.getNodeName().equals("xmlns:xml")) {
                // skip the implicit XML namespace as it provides no value
                continue;
            }

            String prefix = namespaces.item(i).getNodeName();
            if (prefix.equals("xmlns")) {
                prefix = "DEFAULT";
            }

            // add to map
            if (!map.containsKey(prefix)) {
                map.put(prefix, new HashSet<String>());
            }
            map.get(prefix).add(namespaces.item(i).getNodeValue());
        }

        LOG.trace("Namespaces discovered in message: {}.", map);
    }

    protected Object doInEvaluateAs(XPathExpression xpathExpression, Exchange exchange, QName resultQName) {
        LOG.trace("Evaluating exchange: {} as: {}", exchange, resultQName);

        Object answer;

        // set exchange and variable resolver as thread locals for concurrency
        this.exchange.set(exchange);

        // the underlying input stream, which we need to close to avoid locking files or other resources
        InputStream is = null;
        try {
            Object document;
            // only convert to input stream if really needed
            if (isInputStreamNeeded(exchange)) {
                is = exchange.getIn().getBody(InputStream.class);
                document = getDocument(exchange, is);
            } else {
                Object body = exchange.getIn().getBody();
                document = getDocument(exchange, body);
            }
            if (resultQName != null) {
                if (document instanceof InputSource) {
                    InputSource inputSource = (InputSource) document;
                    answer = xpathExpression.evaluate(inputSource, resultQName);
                } else if (document instanceof DOMSource) {
                    DOMSource source = (DOMSource) document;
                    answer = xpathExpression.evaluate(source.getNode(), resultQName);
                } else {
                    answer = xpathExpression.evaluate(document, resultQName);
                }
            } else {
                if (document instanceof InputSource) {
                    InputSource inputSource = (InputSource) document;
                    answer = xpathExpression.evaluate(inputSource);
                } else if (document instanceof DOMSource) {
                    DOMSource source = (DOMSource) document;
                    answer = xpathExpression.evaluate(source.getNode());
                } else {
                    answer = xpathExpression.evaluate(document);
                }
            }
        } catch (XPathExpressionException e) {
            throw new InvalidXPathExpression(getText(), e);
        } finally {
            // IOHelper can handle if is is null
            IOHelper.close(is);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Done evaluating exchange: {} as: {} with result: {}", new Object[]{exchange, resultQName, answer});
        }
        return answer;
    }

    protected synchronized XPathExpression createXPathExpression() throws XPathExpressionException, XPathFactoryConfigurationException {
        // XPathFactory is not thread safe
        XPath xPath = getXPathFactory().newXPath();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating new XPath expression in pool. Namespaces on XPath expression: {}", getNamespaceContext().toString());
        }
        xPath.setNamespaceContext(getNamespaceContext());
        xPath.setXPathVariableResolver(getVariableResolver());

        XPathFunctionResolver parentResolver = getFunctionResolver();
        if (parentResolver == null) {
            parentResolver = xPath.getXPathFunctionResolver();
        }
        xPath.setXPathFunctionResolver(createDefaultFunctionResolver(parentResolver));
        return xPath.compile(text);
    }

    protected synchronized XPathExpression createTraceNamespaceExpression() throws XPathFactoryConfigurationException, XPathExpressionException {
        // XPathFactory is not thread safe
        XPath xPath = getXPathFactory().newXPath();
        return xPath.compile(OBTAIN_ALL_NS_XPATH);
    }

    /**
     * Populate a number of standard prefixes if they are not already there
     */
    protected void populateDefaultNamespaces(DefaultNamespaceContext context) {
        setNamespaceIfNotPresent(context, "in", IN_NAMESPACE);
        setNamespaceIfNotPresent(context, "out", OUT_NAMESPACE);
        setNamespaceIfNotPresent(context, "env", Namespaces.ENVIRONMENT_VARIABLES);
        setNamespaceIfNotPresent(context, "system", Namespaces.SYSTEM_PROPERTIES_NAMESPACE);
        setNamespaceIfNotPresent(context, "function", Namespaces.FUNCTION_NAMESPACE);
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
                    if (isMatchingNamespaceOrEmptyNamespace(qName.getNamespaceURI(), FUNCTION_NAMESPACE)) {
                        String localPart = qName.getLocalPart();
                        if (localPart.equals("properties") && argumentCount == 1) {
                            return getPropertiesFunction();
                        }
                        if (localPart.equals("simple") && argumentCount == 1) {
                            return getSimpleFunction();
                        }
                    }
                }
                return answer;
            }
        };
    }

    /**
     * Checks whether we need an {@link InputStream} to access the message body.
     * <p/>
     * Depending on the content in the message body, we may not need to convert
     * to {@link InputStream}.
     *
     * @param exchange the current exchange
     * @return <tt>true</tt> to convert to {@link InputStream} beforehand converting afterwards.
     */
    protected boolean isInputStreamNeeded(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return false;
        }

        if (body instanceof WrappedFile) {
            body = ((WrappedFile) body).getFile();
        }
        if (body instanceof File) {
            // input stream is needed for File to avoid locking the file in case of errors etc
            return true;
        }

        // input stream is not needed otherwise
        return false;
    }

    /**
     * Strategy method to extract the document from the exchange.
     */
    @SuppressWarnings("unchecked")
    protected Object getDocument(Exchange exchange, Object body) {
        Object answer = null;

        Class type = getDocumentType();
        if (type != null) {
            // try to get the body as the desired type
            answer = exchange.getContext().getTypeConverter().convertTo(type, exchange, body);
        }
        // fallback to get the body as is
        if (answer == null) {
            answer = body;
        }

        // let's try coercing some common types into something JAXP can work with
        if (answer instanceof WrappedFile) {
            // special for files so we can work with them out of the box
            InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, answer);
            answer = new InputSource(is);
        } else if (answer instanceof BeanInvocation) {
            // if its a null bean invocation then handle that
            BeanInvocation bi = exchange.getContext().getTypeConverter().convertTo(BeanInvocation.class, answer);
            if (bi.getArgs() != null && bi.getArgs().length == 1 && bi.getArgs()[0] == null) {
                // its a null argument from the bean invocation so use null as answer
                answer = null;
            }
        } else if (answer instanceof String) {
            answer = new InputSource(new StringReader(answer.toString()));
        }

        // call the reset if the in message body is StreamCache
        MessageHelper.resetStreamCache(exchange.getIn());
        return answer;
    }

    private MessageVariableResolver getVariableResolver() {
        MessageVariableResolver resolver = variableResolver.get();
        if (resolver == null) {
            resolver = new MessageVariableResolver(exchange);
            variableResolver.set(resolver);
        }
        return resolver;
    }

    public void start() throws Exception {
        if (xpathFactory == null) {
            initDefaultXPathFactory();
        }
    }

    public void stop() throws Exception {
        pool.clear();
        poolTraceNamespaces.clear();
    }

    protected synchronized void initDefaultXPathFactory() throws XPathFactoryConfigurationException {
        if (defaultXPathFactory == null) {
            if (objectModelUri != null) {
                defaultXPathFactory = XPathFactory.newInstance(objectModelUri);
                LOG.info("Using objectModelUri " + objectModelUri + " when created XPathFactory {}", defaultXPathFactory);
            }

            if (defaultXPathFactory == null) {
                // read system property and see if there is a factory set
                Properties properties = System.getProperties();
                for (Map.Entry prop : properties.entrySet()) {
                    String key = (String) prop.getKey();
                    if (key.startsWith(XPathFactory.DEFAULT_PROPERTY_NAME)) {
                        String uri = ObjectHelper.after(key, ":");
                        if (uri != null) {
                            defaultXPathFactory = XPathFactory.newInstance(uri);
                            LOG.info("Using system property {} with value {} when created XPathFactory {}", new Object[]{key, uri, defaultXPathFactory});
                        }
                    }
                }
            }

            defaultXPathFactory = XPathFactory.newInstance();
            LOG.info("Created default XPathFactory {}", defaultXPathFactory);
        }
    }

    /**
     * On completion class which cleanup thread local resources
     */
    private final class XPathBuilderOnCompletion extends SynchronizationAdapter {

        @Override
        public void onDone(Exchange exchange) {
            // when the exchange is done, then cleanup thread locals if they are still
            // pointing to this exchange that was done
            if (exchange.equals(XPathBuilder.this.exchange.get())) {
                // cleanup thread locals after usage
                XPathBuilder.this.variableResolver.remove();
                XPathBuilder.this.exchange.remove();
            }
        }

        @Override
        public boolean allowHandover() {
            // this completion should not be handed over, as we want to execute it
            // on current thread as the thread locals is bound the current thread
            return false;
        }

        @Override
        public String toString() {
            return "XPathBuilderOnCompletion";
        }
    }

}
