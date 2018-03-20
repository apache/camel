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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
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
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.WrappedFile;
import org.apache.camel.converter.jaxp.ThreadSafeNodeList;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.support.ServiceSupport;
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
 * This implementation is thread safe by using thread locals and pooling to allow concurrency.
 * <p/>
 * <b>Important:</b> After configuring the {@link XPathBuilder} its advised to invoke {@link #start()}
 * to prepare the builder before using; though the builder will auto-start on first use.
 *
 * @see XPathConstants#NODESET
 */
public class XPathBuilder extends ServiceSupport implements CamelContextAware, Expression, Predicate, NamespaceAware {
    private static final Logger LOG = LoggerFactory.getLogger(XPathBuilder.class);
    private static final String SAXON_OBJECT_MODEL_URI = "http://saxon.sf.net/jaxp/xpath/om";
    private static final String SAXON_FACTORY_CLASS_NAME = "net.sf.saxon.xpath.XPathFactoryImpl";
    private static final String OBTAIN_ALL_NS_XPATH = "//*/namespace::*";

    private static volatile XPathFactory defaultXPathFactory;

    private CamelContext camelContext;
    private final Queue<XPathExpression> pool = new ConcurrentLinkedQueue<XPathExpression>();
    private final Queue<XPathExpression> poolLogNamespaces = new ConcurrentLinkedQueue<XPathExpression>();
    private final String text;
    private final ThreadLocal<Exchange> exchange = new ThreadLocal<Exchange>();
    private final MessageVariableResolver variableResolver = new MessageVariableResolver(exchange);
    private final Map<String, String> namespaces = new ConcurrentHashMap<String, String>();
    private boolean threadSafety;
    private volatile XPathFactory xpathFactory;
    private volatile Class<?> documentType = Document.class;
    // For some reason the default expression of "a/b" on a document such as
    // <a><b>1</b><b>2</b></a>
    // will evaluate as just "1" by default which is bizarre. So by default
    // let's assume XPath expressions result in nodesets.
    private volatile Class<?> resultType;
    private volatile QName resultQName = XPathConstants.NODESET;
    private volatile String objectModelUri;
    private volatile String factoryClassName;
    private volatile DefaultNamespaceContext namespaceContext;
    private volatile boolean logNamespaces;
    private volatile XPathFunctionResolver functionResolver;
    private volatile XPathFunction bodyFunction;
    private volatile XPathFunction headerFunction;
    private volatile XPathFunction outBodyFunction;
    private volatile XPathFunction outHeaderFunction;
    private volatile XPathFunction propertiesFunction;
    private volatile XPathFunction simpleFunction;
    /**
     * The name of the header we want to apply the XPath expression to, which when set will cause
     * the xpath to be evaluated on the required header, otherwise it will be applied to the body
     */
    private volatile String headerName;

    /**
     * @param text The XPath expression
     */
    public XPathBuilder(String text) {
        this.text = text;
    }

    /**
     * @param text The XPath expression
     * @return A new XPathBuilder object
     */
    public static XPathBuilder xpath(String text) {
        return new XPathBuilder(text);
    }

    /**
     * @param text       The XPath expression
     * @param resultType The result type that the XPath expression will return.
     * @return A new XPathBuilder object
     */
    public static XPathBuilder xpath(String text, Class<?> resultType) {
        XPathBuilder builder = new XPathBuilder(text);
        builder.setResultType(resultType);
        return builder;
    }

    @Override
    public String toString() {
        return "XPath: " + text;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean matches(Exchange exchange) {
        try {
            Object booleanResult = evaluateAs(exchange, XPathConstants.BOOLEAN);
            return exchange.getContext().getTypeConverter().convertTo(Boolean.class, booleanResult);
        } finally {
            // remove the thread local after usage
            this.exchange.remove();
        }
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        try {
            Object result = evaluate(exchange);
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, result);
        } finally {
            // remove the thread local after usage
            this.exchange.remove();
        }
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

        try {
            return matches(dummy);
        } finally {
            // remove the thread local after usage
            exchange.remove();
        }
    }

    /**
     * Evaluates the given xpath using the provided body.
     * <p/>
     * The evaluation uses by default {@link javax.xml.xpath.XPathConstants#NODESET} as the type
     * used during xpath evaluation. The output from xpath is then afterwards type converted
     * using Camel's type converter to the given type.
     * <p/>
     * If you want to evaluate xpath using a different type, then call {@link #setResultType(Class)}
     * prior to calling this evaluate method.
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

        try {
            return evaluate(dummy, type);
        } finally {
            // remove the thread local after usage
            exchange.remove();
        }
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
        setResultType(String.class);
        try {
            return evaluate(dummy, String.class);
        } finally {
            // remove the thread local after usage
            this.exchange.remove();
        }
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Sets the expression result type to {@link XPathConstants#BOOLEAN}
     *
     * @return the current builder
     */
    public XPathBuilder booleanResult() {
        resultQName = XPathConstants.BOOLEAN;
        return this;
    }

    /**
     * Sets the expression result type to {@link XPathConstants#NODE}
     *
     * @return the current builder
     */
    public XPathBuilder nodeResult() {
        resultQName = XPathConstants.NODE;
        return this;
    }

    /**
     * Sets the expression result type to {@link XPathConstants#NODESET}
     *
     * @return the current builder
     */
    public XPathBuilder nodeSetResult() {
        resultQName = XPathConstants.NODESET;
        return this;
    }

    /**
     * Sets the expression result type to {@link XPathConstants#NUMBER}
     *
     * @return the current builder
     */
    public XPathBuilder numberResult() {
        resultQName = XPathConstants.NUMBER;
        return this;
    }

    /**
     * Sets the expression result type to {@link XPathConstants#STRING}
     *
     * @return the current builder
     */
    public XPathBuilder stringResult() {
        resultQName = XPathConstants.STRING;
        return this;
    }

    /**
     * Sets the expression result type to the given {@code resultType}
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
        // Careful! Setting the Object Model URI this way will set the *Default* XPath Factory, which since is a static field,
        // will set the XPath Factory system-wide. Decide what to do, as changing this behaviour can break compatibility. Provided the setObjectModel which changes
        // this instance's XPath Factory rather than the static field
        this.objectModelUri = uri;
        return this;
    }


    /**
     * Sets the factory class name to use
     *
     * @return the current builder
     */
    public XPathBuilder factoryClassName(String factoryClassName) {
        this.factoryClassName = factoryClassName;
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
        this.factoryClassName = SAXON_FACTORY_CLASS_NAME;
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
        namespaces.put(prefix, uri);
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
    public XPathBuilder logNamespaces() {
        setLogNamespaces(true);
        return this;
    }

    /**
     * Whether to enable thread-safety for the returned result of the xpath expression.
     * This applies to when using NODESET as the result type, and the returned set has
     * multiple elements. In this situation there can be thread-safety issues if you
     * process the NODESET concurrently such as from a Camel Splitter EIP in parallel processing mode.
     * This option prevents concurrency issues by doing defensive copies of the nodes.
     * <p/>
     * It is recommended to turn this option on if you are using camel-saxon or Saxon in your application.
     * Saxon has thread-safety issues which can be prevented by turning this option on.
     * <p/>
     * Thread-safety is disabled by default
     *
     * @return the current builder.
     */
    public XPathBuilder threadSafety(boolean threadSafety) {
        setThreadSafety(threadSafety);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    /**
     * Gets the xpath factory, can be <tt>null</tt> if no custom factory has been assigned.
     * <p/>
     * A default factory will be assigned (if no custom assigned) when either starting this builder
     * or on first evaluation.
     *
     * @return the factory, or <tt>null</tt> if this builder has not been started/used before.
     */
    public XPathFactory getXPathFactory() {
        return xpathFactory;
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

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isThreadSafety() {
        return threadSafety;
    }

    public void setThreadSafety(boolean threadSafety) {
        this.threadSafety = threadSafety;
    }

    /**
     * Gets the namespace context, can be <tt>null</tt> if no custom context has been assigned.
     * <p/>
     * A default context will be assigned (if no custom assigned) when either starting this builder
     * or on first evaluation.
     *
     * @return the context, or <tt>null</tt> if this builder has not been started/used before.
     */
    public DefaultNamespaceContext getNamespaceContext() {
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
        this.namespaces.clear();
        this.namespaces.putAll(namespaces);
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    /**
     * Gets the {@link XPathFunction} for getting the input message body.
     * <p/>
     * A default function will be assigned (if no custom assigned) when either starting this builder
     * or on first evaluation.
     *
     * @return the function, or <tt>null</tt> if this builder has not been started/used before.
     */
    public XPathFunction getBodyFunction() {
        return bodyFunction;
    }

    private XPathFunction createBodyFunction() {
        return new XPathFunction() {
            @SuppressWarnings("rawtypes")
            public Object evaluate(List list) throws XPathFunctionException {
                return exchange.get().getIn().getBody();
            }
        };
    }

    public void setBodyFunction(XPathFunction bodyFunction) {
        this.bodyFunction = bodyFunction;
    }

    /**
     * Gets the {@link XPathFunction} for getting the input message header.
     * <p/>
     * A default function will be assigned (if no custom assigned) when either starting this builder
     * or on first evaluation.
     *
     * @return the function, or <tt>null</tt> if this builder has not been started/used before.
     */
    public XPathFunction getHeaderFunction() {
        return headerFunction;
    }

    private XPathFunction createHeaderFunction() {
        return new XPathFunction() {
            @SuppressWarnings("rawtypes")
            public Object evaluate(List list) throws XPathFunctionException {
                if (!list.isEmpty()) {
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

    public void setHeaderFunction(XPathFunction headerFunction) {
        this.headerFunction = headerFunction;
    }

    /**
     * Gets the {@link XPathFunction} for getting the output message body.
     * <p/>
     * A default function will be assigned (if no custom assigned) when either starting this builder
     * or on first evaluation.
     *
     * @return the function, or <tt>null</tt> if this builder has not been started/used before.
     */
    public XPathFunction getOutBodyFunction() {
        return outBodyFunction;
    }

    private XPathFunction createOutBodyFunction() {
        return new XPathFunction() {
            @SuppressWarnings("rawtypes")
            public Object evaluate(List list) throws XPathFunctionException {
                if (exchange.get() != null && exchange.get().hasOut()) {
                    return exchange.get().getOut().getBody();
                }
                return null;
            }
        };
    }

    public void setOutBodyFunction(XPathFunction outBodyFunction) {
        this.outBodyFunction = outBodyFunction;
    }

    /**
     * Gets the {@link XPathFunction} for getting the output message header.
     * <p/>
     * A default function will be assigned (if no custom assigned) when either starting this builder
     * or on first evaluation.
     *
     * @return the function, or <tt>null</tt> if this builder has not been started/used before.
     */
    public XPathFunction getOutHeaderFunction() {
        return outHeaderFunction;
    }

    private XPathFunction createOutHeaderFunction() {
        return new XPathFunction() {
            @SuppressWarnings("rawtypes")
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

    public void setOutHeaderFunction(XPathFunction outHeaderFunction) {
        this.outHeaderFunction = outHeaderFunction;
    }

    /**
     * Gets the {@link XPathFunction} for getting the exchange properties.
     * <p/>
     * A default function will be assigned (if no custom assigned) when either starting this builder
     * or on first evaluation.
     *
     * @return the function, or <tt>null</tt> if this builder has not been started/used before.
     */
    public XPathFunction getPropertiesFunction() {
        return propertiesFunction;
    }

    private XPathFunction createPropertiesFunction() {
        return new XPathFunction() {
            @SuppressWarnings("rawtypes")
            public Object evaluate(List list) throws XPathFunctionException {
                if (!list.isEmpty()) {
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

    public void setPropertiesFunction(XPathFunction propertiesFunction) {
        this.propertiesFunction = propertiesFunction;
    }

    /**
     * Gets the {@link XPathFunction} for executing <a href="http://camel.apache.org/simple">simple</a>
     * language as xpath function.
     * <p/>
     * A default function will be assigned (if no custom assigned) when either starting this builder
     * or on first evaluation.
     *
     * @return the function, or <tt>null</tt> if this builder has not been started/used before.
     */
    public XPathFunction getSimpleFunction() {
        return simpleFunction;
    }

    private XPathFunction createSimpleFunction() {
        return new XPathFunction() {
            @SuppressWarnings("rawtypes")
            public Object evaluate(List list) throws XPathFunctionException {
                if (!list.isEmpty()) {
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

    public void setLogNamespaces(boolean logNamespaces) {
        this.logNamespaces = logNamespaces;
    }

    public boolean isLogNamespaces() {
        return logNamespaces;
    }

    /**
     * Enables Saxon on this particular XPath expression, as {@link #saxon()} sets the default static XPathFactory which may have already been initialised
     * by previous XPath expressions
     */
    public void enableSaxon() {
        this.setObjectModelUri(SAXON_OBJECT_MODEL_URI);
        this.setFactoryClassName(SAXON_FACTORY_CLASS_NAME);
    }

    public String getObjectModelUri() {
        return objectModelUri;
    }

    public void setObjectModelUri(String objectModelUri) {
        this.objectModelUri = objectModelUri;
    }

    public String getFactoryClassName() {
        return factoryClassName;
    }

    public void setFactoryClassName(String factoryClassName) {
        this.factoryClassName = factoryClassName;
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
            if (logNamespaces && LOG.isInfoEnabled()) {
                logNamespaces(exchange);
            }
            return doInEvaluateAs(xpathExpression, exchange, resultQName);
        } finally {
            // release it back to the pool
            pool.add(xpathExpression);
            LOG.trace("Released XPathExpression back to pool");
        }
    }

    private void logNamespaces(Exchange exchange) {
        InputStream is = null;
        NodeList answer = null;
        XPathExpression xpathExpression = null;

        try {
            xpathExpression = poolLogNamespaces.poll();
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
            } else if (document instanceof SAXSource) {
                SAXSource source = (SAXSource) document;
                // since its a SAXSource it may not return an NodeList (for example if using Saxon)
                Object result = xpathExpression.evaluate(source.getInputSource(), XPathConstants.NODESET);
                if (result instanceof NodeList) {
                    answer = (NodeList) result;
                } else {
                    answer = null;
                }
            } else {
                answer = (NodeList) xpathExpression.evaluate(document, XPathConstants.NODESET);
            }
        } catch (Exception e) {
            LOG.warn("Unable to trace discovered namespaces in XPath expression", e);
        } finally {
            // IOHelper can handle if is is null
            IOHelper.close(is);
            poolLogNamespaces.add(xpathExpression);
        }

        if (answer != null) {
            logDiscoveredNamespaces(answer);
        }
    }

    private void logDiscoveredNamespaces(NodeList namespaces) {
        Map<String, HashSet<String>> map = new LinkedHashMap<String, HashSet<String>>();
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

        LOG.info("Namespaces discovered in message: {}.", map);
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

            // Check if we need to apply the XPath expression to a header
            if (ObjectHelper.isNotEmpty(getHeaderName())) {
                String headerName = getHeaderName();
                // only convert to input stream if really needed
                if (isInputStreamNeeded(exchange, headerName)) {
                    is = exchange.getIn().getHeader(headerName, InputStream.class);
                    document = getDocument(exchange, is);
                } else {
                    Object headerObject = exchange.getIn().getHeader(getHeaderName());
                    document = getDocument(exchange, headerObject);
                }
            } else {
                // only convert to input stream if really needed
                if (isInputStreamNeeded(exchange)) {
                    is = exchange.getIn().getBody(InputStream.class);
                    document = getDocument(exchange, is);
                } else {
                    Object body = exchange.getIn().getBody();
                    document = getDocument(exchange, body);
                }
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
            String message = getText();
            if (ObjectHelper.isNotEmpty(getHeaderName())) {
                message = message + " with headerName " + getHeaderName();
            }
            throw new InvalidXPathExpression(message, e);
        } finally {
            // IOHelper can handle if is is null
            IOHelper.close(is);
        }

        if (threadSafety && answer != null && answer instanceof NodeList) {
            try {
                NodeList list = (NodeList) answer;

                // when the result is NodeList and it has 2+ elements then its not thread-safe to use concurrently
                // and we need to clone each node and build a thread-safe list to be used instead
                boolean threadSafetyNeeded = list.getLength() >= 2;
                if (threadSafetyNeeded) {
                    answer = new ThreadSafeNodeList(list);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created thread-safe result from: {} as: {}", list.getClass().getName(), answer.getClass().getName());
                    }
                }
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Done evaluating exchange: {} as: {} with result: {}", new Object[]{exchange, resultQName, answer});
        }
        return answer;
    }

    /**
     * Creates a new xpath expression as there we no available in the pool.
     * <p/>
     * This implementation must be synchronized to ensure thread safety, as this XPathBuilder instance may not have been
     * started prior to being used.
     */
    protected synchronized XPathExpression createXPathExpression() throws XPathExpressionException, XPathFactoryConfigurationException {
        // ensure we are started
        try {
            start();
        } catch (Exception e) {
            throw new RuntimeExpressionException("Error starting XPathBuilder", e);
        }

        // XPathFactory is not thread safe
        XPath xPath = getXPathFactory().newXPath();

        if (!logNamespaces && LOG.isTraceEnabled()) {
            LOG.trace("Creating new XPath expression in pool. Namespaces on XPath expression: {}", getNamespaceContext().toString());
        } else if (logNamespaces && LOG.isInfoEnabled()) {
            LOG.info("Creating new XPath expression in pool. Namespaces on XPath expression: {}", getNamespaceContext().toString());
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

    protected DefaultNamespaceContext createNamespaceContext(XPathFactory factory) {
        DefaultNamespaceContext context = new DefaultNamespaceContext(factory);
        populateDefaultNamespaces(context);
        return context;
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
        return isInputStreamNeededForObject(exchange, body);
    }

    /**
     * Checks whether we need an {@link InputStream} to access the message header.
     * <p/>
     * Depending on the content in the message header, we may not need to convert
     * to {@link InputStream}.
     *
     * @param exchange the current exchange
     * @return <tt>true</tt> to convert to {@link InputStream} beforehand converting afterwards.
     */
    protected boolean isInputStreamNeeded(Exchange exchange, String headerName) {
        Object header = exchange.getIn().getHeader(headerName);
        return isInputStreamNeededForObject(exchange, header);
    }

    /**
     * Checks whether we need an {@link InputStream} to access this object
     * <p/>
     * Depending on the content in the object, we may not need to convert
     * to {@link InputStream}.
     *
     * @param exchange the current exchange
     * @return <tt>true</tt> to convert to {@link InputStream} beforehand converting afterwards.
     */
    protected boolean isInputStreamNeededForObject(Exchange exchange, Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof WrappedFile) {
            obj = ((WrappedFile<?>) obj).getFile();
        }
        if (obj instanceof File) {
            // input stream is needed for File to avoid locking the file in case of errors etc
            return true;
        }

        // input stream is not needed otherwise
        return false;
    }

    /**
     * Strategy method to extract the document from the exchange.
     */
    protected Object getDocument(Exchange exchange, Object body) {
        try {
            return doGetDocument(exchange, body);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            // call the reset if the in message body is StreamCache
            MessageHelper.resetStreamCache(exchange.getIn());
        }
    }

    protected Object doGetDocument(Exchange exchange, Object body) throws Exception {
        if (body == null) {
            return null;
        }

        Object answer = null;

        Class<?> type = getDocumentType();
        Exception cause = null;
        if (type != null) {
            // try to get the body as the desired type
            try {
                answer = exchange.getContext().getTypeConverter().convertTo(type, exchange, body);
            } catch (Exception e) {
                // we want to store the caused exception, if we could not convert
                cause = e;
            }
        }

        if (type == null && answer == null) {
            // fallback to get the body as is
            answer = body;
        } else if (answer == null) {
            // there was a type, and we could not convert to it, then fail
            if (cause != null) {
                throw cause;
            } else {
                throw new NoTypeConversionAvailableException(body, type);
            }
        }

        return answer;
    }

    private MessageVariableResolver getVariableResolver() {
        return variableResolver;
    }

    @Override
    public void doStart() throws Exception {
        if (xpathFactory == null) {
            xpathFactory = createXPathFactory();
        }
        if (namespaceContext == null) {
            namespaceContext = createNamespaceContext(xpathFactory);
        }
        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            namespaceContext.add(entry.getKey(), entry.getValue());
        }

        // create default functions if no custom assigned
        if (bodyFunction == null) {
            bodyFunction = createBodyFunction();
        }
        if (headerFunction == null) {
            headerFunction = createHeaderFunction();
        }
        if (outBodyFunction == null) {
            outBodyFunction = createOutBodyFunction();
        }
        if (outHeaderFunction == null) {
            outHeaderFunction = createOutHeaderFunction();
        }
        if (propertiesFunction == null) {
            propertiesFunction = createPropertiesFunction();
        }
        if (simpleFunction == null) {
            simpleFunction = createSimpleFunction();
        }
    }

    @Override
    public void doStop() throws Exception {
        pool.clear();
        poolLogNamespaces.clear();
    }

    protected synchronized XPathFactory createXPathFactory() throws XPathFactoryConfigurationException {
        if (objectModelUri != null) {
            String xpathFactoryClassName = factoryClassName;
            if (objectModelUri.equals(SAXON_OBJECT_MODEL_URI) && (xpathFactoryClassName == null || SAXON_FACTORY_CLASS_NAME.equals(xpathFactoryClassName))) {
                // from Saxon 9.7 onwards you should favour to create the class directly
                // https://www.saxonica.com/html/documentation/xpath-api/jaxp-xpath/factory.html
                try {
                    if (camelContext != null) {
                        Class<XPathFactory> clazz = camelContext.getClassResolver().resolveClass(SAXON_FACTORY_CLASS_NAME, XPathFactory.class);
                        if (clazz != null) {
                            LOG.debug("Creating Saxon XPathFactory using class: {})", clazz);
                            xpathFactory = camelContext.getInjector().newInstance(clazz);
                            LOG.info("Created Saxon XPathFactory: {}", xpathFactory);
                        }
                    }
                } catch (Throwable e) {
                    LOG.warn("Attempted to create Saxon XPathFactory by creating a new instance of " + SAXON_FACTORY_CLASS_NAME
                        + " failed. Will fallback and create XPathFactory using JDK API. This exception is ignored (stacktrace in DEBUG logging level).");
                    LOG.debug("Error creating Saxon XPathFactory. This exception is ignored.", e);
                }
            }

            if (xpathFactory == null) {
                LOG.debug("Creating XPathFactory from objectModelUri: {}", objectModelUri);
                xpathFactory = ObjectHelper.isEmpty(xpathFactoryClassName)
                    ? XPathFactory.newInstance(objectModelUri)
                    : XPathFactory.newInstance(objectModelUri, xpathFactoryClassName, null);
                LOG.info("Created XPathFactory: {} from objectModelUri: {}", xpathFactory, objectModelUri);
            }

            return xpathFactory;
        }

        if (defaultXPathFactory == null) {
            defaultXPathFactory = createDefaultXPathFactory();
        }
        return defaultXPathFactory;
    }

    protected static XPathFactory createDefaultXPathFactory() throws XPathFactoryConfigurationException {
        XPathFactory factory = null;

        // read system property and see if there is a factory set
        Properties properties = System.getProperties();
        for (Map.Entry<Object, Object> prop : properties.entrySet()) {
            String key = (String) prop.getKey();
            if (key.startsWith(XPathFactory.DEFAULT_PROPERTY_NAME)) {
                String uri = ObjectHelper.after(key, ":");
                if (uri != null) {
                    factory = XPathFactory.newInstance(uri);
                    LOG.info("Using system property {} with value {} when created default XPathFactory {}", new Object[]{key, uri, factory});
                }
            }
        }

        if (factory == null) {
            factory = XPathFactory.newInstance();
            LOG.info("Created default XPathFactory {}", factory);
        }

        return factory;
    }

}
