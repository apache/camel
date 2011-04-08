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
package org.apache.camel.component.xquery;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Node;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.component.bean.BeanInvocation;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.BytesSource;
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an XQuery builder.
 * <p/>
 * The XQueryExpression, as you would expect, can be executed repeatedly, as often as you want, in the same or in different threads.
 *
 * @version 
 */
public abstract class XQueryBuilder implements Expression, Predicate, NamespaceAware, Processor {
    private static final transient Logger LOG = LoggerFactory.getLogger(XQueryBuilder.class);
    private Configuration configuration;
    private XQueryExpression expression;
    private StaticQueryContext staticQueryContext;
    private Map<String, Object> parameters = new HashMap<String, Object>();
    private Map<String, String> namespacePrefixes = new HashMap<String, String>();
    private XmlConverter converter = new XmlConverter();
    private ResultFormat resultsFormat = ResultFormat.DOM;
    private Properties properties = new Properties();
    private Class resultType;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private boolean stripsAllWhiteSpace = true;

    @Override
    public String toString() {
        return "XQuery[" + expression + "]";
    }

    public void process(Exchange exchange) throws Exception {
        Object body = evaluate(exchange);
        exchange.getOut().setBody(body);

        // propagate headers
        exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
    }
    
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, result);
    }

    @SuppressWarnings("unchecked")
    public Object evaluate(Exchange exchange) {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Evaluation: " + expression + " for exchange: " + exchange);
            }

            if (resultType != null) {
                if (resultType.equals(String.class)) {
                    return evaluateAsString(exchange);
                } else if (resultType.isAssignableFrom(Collection.class)) {
                    return evaluateAsList(exchange);
                } else if (resultType.isAssignableFrom(Node.class)) {
                    return evaluateAsDOM(exchange);
                } else {
                    throw new IllegalArgumentException("ResultType: " + resultType.getCanonicalName() + " not supported");
                }
            }
            switch (resultsFormat) {
            case Bytes:
                return evaluateAsBytes(exchange);
            case BytesSource:
                return evaluateAsBytesSource(exchange);
            case DOM:
                return evaluateAsDOM(exchange);
            case List:
                return evaluateAsList(exchange);
            case StringSource:
                return evaluateAsStringSource(exchange);
            case String:
            default:
                return evaluateAsString(exchange);
            }
        } catch (Exception e) {
            throw new RuntimeExpressionException(e);
        }
    }

    public List evaluateAsList(Exchange exchange) throws Exception {
        initialize(exchange);

        return getExpression().evaluate(createDynamicContext(exchange));
    }

    public Object evaluateAsStringSource(Exchange exchange) throws Exception {
        initialize(exchange);

        String text = evaluateAsString(exchange);
        return new StringSource(text);
    }

    public Object evaluateAsBytesSource(Exchange exchange) throws Exception {
        initialize(exchange);

        byte[] bytes = evaluateAsBytes(exchange);
        return new BytesSource(bytes);
    }

    public Node evaluateAsDOM(Exchange exchange) throws Exception {
        initialize(exchange);

        DOMResult result = new DOMResult();
        DynamicQueryContext context = createDynamicContext(exchange);
        XQueryExpression expression = getExpression();
        expression.pull(context, result, properties);
        return result.getNode();
    }

    public byte[] evaluateAsBytes(Exchange exchange) throws Exception {
        initialize(exchange);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Result result = new StreamResult(buffer);
        getExpression().pull(createDynamicContext(exchange), result, properties);
        byte[] bytes = buffer.toByteArray();
        return bytes;
    }

    public String evaluateAsString(Exchange exchange) throws Exception {
        initialize(exchange);

        StringWriter buffer = new StringWriter();
        SequenceIterator iter = getExpression().iterator(createDynamicContext(exchange));
        for (Item item = iter.next(); item != null; item = iter.next()) {
            buffer.append(item.getStringValueCS());
        }
        return buffer.toString();
    }

    public boolean matches(Exchange exchange) {
        try {
            List list = evaluateAsList(exchange);
            return matches(exchange, list);
        } catch (Exception e) {
            throw new RuntimeExpressionException(e);
        }
    }

    public void assertMatches(String text, Exchange exchange) throws AssertionError {
        List list;

        try {
            list = evaluateAsList(exchange);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        if (!matches(exchange, list)) {
            throw new AssertionError(this + " failed on " + exchange + " as evaluated: " + list);
        }
    }

    // Static helper methods
    //-------------------------------------------------------------------------
    public static XQueryBuilder xquery(final String queryText) {
        return new XQueryBuilder() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext)
                throws XPathException {
                return staticQueryContext.compileQuery(queryText);
            }
        };
    }

    public static XQueryBuilder xquery(final Reader reader) {
        return new XQueryBuilder() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext)
                throws XPathException, IOException {
                return staticQueryContext.compileQuery(reader);
            }
        };
    }

    public static XQueryBuilder xquery(final InputStream in, final String characterSet) {
        return new XQueryBuilder() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext)
                throws XPathException, IOException {
                return staticQueryContext.compileQuery(in, characterSet);
            }
        };
    }

    public static XQueryBuilder xquery(File file, String characterSet) throws IOException {
        return xquery(IOConverter.toInputStream(file), characterSet);
    }

    public static XQueryBuilder xquery(URL url, String characterSet) throws IOException {
        return xquery(IOConverter.toInputStream(url), characterSet);
    }

    public static XQueryBuilder xquery(File file) throws IOException {
        return xquery(IOConverter.toInputStream(file), ObjectHelper.getDefaultCharacterSet());
    }

    public static XQueryBuilder xquery(URL url) throws IOException {
        return xquery(IOConverter.toInputStream(url), ObjectHelper.getDefaultCharacterSet());
    }

    // Fluent API
    // -------------------------------------------------------------------------
    public XQueryBuilder parameter(String name, Object value) {
        parameters.put(name, value);
        return this;
    }

    public XQueryBuilder namespace(String prefix, String uri) {
        namespacePrefixes.put(prefix, uri);
        // more namespace, we must re initialize
        initialized.set(false);
        return this;
    }

    public XQueryBuilder resultType(Class resultType) {
        setResultType(resultType);
        return this;
    }

    public XQueryBuilder asBytes() {
        setResultsFormat(ResultFormat.Bytes);
        return this;
    }

    public XQueryBuilder asBytesSource() {
        setResultsFormat(ResultFormat.BytesSource);
        return this;
    }

    public XQueryBuilder asDOM() {
        setResultsFormat(ResultFormat.DOM);
        return this;
    }

    public XQueryBuilder asDOMSource() {
        setResultsFormat(ResultFormat.DOMSource);
        return this;
    }

    public XQueryBuilder asList() {
        setResultsFormat(ResultFormat.List);
        return this;
    }

    public XQueryBuilder asString() {
        setResultsFormat(ResultFormat.String);
        return this;
    }

    public XQueryBuilder asStringSource() {
        setResultsFormat(ResultFormat.StringSource);
        return this;
    }

    public XQueryBuilder stripsAllWhiteSpace() {
        setStripsAllWhiteSpace(true);
        return this;
    }

    public XQueryBuilder stripsIgnorableWhiteSpace() {
        setStripsAllWhiteSpace(false);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    /**
     * Configures the namespace context from the given DOM element
     */
    public void setNamespaces(Map<String, String> namespaces) {
        namespacePrefixes.putAll(namespaces);
        // more namespace, we must re initialize
        initialized.set(false);
    }

    public XQueryExpression getExpression() throws IOException, XPathException {
        return expression;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
        // change configuration, we must re initialize
        initialized.set(false);
    }

    public StaticQueryContext getStaticQueryContext() {
        return staticQueryContext;
    }

    public void setStaticQueryContext(StaticQueryContext staticQueryContext) {
        this.staticQueryContext = staticQueryContext;
        // change context, we must re initialize
        initialized.set(false);
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public ResultFormat getResultsFormat() {
        return resultsFormat;
    }

    public void setResultsFormat(ResultFormat resultsFormat) {
        this.resultsFormat = resultsFormat;
    }

    public Class getResultType() {
        return resultType;
    }

    public void setResultType(Class resultType) {
        this.resultType = resultType;
    }

    public boolean isStripsAllWhiteSpace() {
        return stripsAllWhiteSpace;
    }

    public void setStripsAllWhiteSpace(boolean stripsAllWhiteSpace) {
        this.stripsAllWhiteSpace = stripsAllWhiteSpace;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * A factory method to create the XQuery expression
     */
    protected abstract XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext)
        throws XPathException, IOException;

    /**
     * Creates a dynamic context for the given exchange
     */
    protected DynamicQueryContext createDynamicContext(Exchange exchange) throws Exception {
        Configuration config = getConfiguration();
        DynamicQueryContext dynamicQueryContext = new DynamicQueryContext(config);

        Message in = exchange.getIn();

        Item item = in.getBody(Item.class);
        if (item != null) {
            dynamicQueryContext.setContextItem(item);
        } else {
            Source source = in.getBody(Source.class);
            if (source == null) {
                Object body = in.getBody();

                // lets try coerce some common types into something JAXP can deal with
                if (body instanceof GenericFile) {
                    // special for files so we can work with them out of the box
                    InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, body);
                    source = converter.toDOMSource(is);
                } else if (body instanceof BeanInvocation) {
                    // if its a null bean invocation then handle that
                    BeanInvocation bi = exchange.getContext().getTypeConverter().convertTo(BeanInvocation.class, body);
                    if (bi.getArgs() != null && bi.getArgs().length == 1 && bi.getArgs()[0] == null) {
                        // its a null argument from the bean invocation so use null as answer
                        source = null;
                    }
                } else if (body instanceof String) {
                    source = converter.toDOMSource(body.toString());
                } else {
                    // try some of Camels type converters
                    InputStream is = in.getBody(InputStream.class);
                    if (is != null) {
                        source = converter.toDOMSource(is);
                    }
                    // fallback and use String
                    if (source == null) {
                        String s = in.getBody(String.class);
                        if (s != null) {
                            source = converter.toDOMSource(s);
                        }
                    }
                }

                if (source == null) {
                    // indicate it was not possible to convert to a Source type
                    throw new NoTypeConversionAvailableException(body, Source.class);
                }
            }
            DocumentInfo doc = getStaticQueryContext().buildDocument(source);
            dynamicQueryContext.setContextItem(doc);
        }
        
        configureQuery(dynamicQueryContext, exchange);
        // call the reset if the in message body is StreamCache
        MessageHelper.resetStreamCache(exchange.getIn());
        return dynamicQueryContext;
    }

    /**
     * Configures the dynamic context with exchange specific parameters
     */
    protected void configureQuery(DynamicQueryContext dynamicQueryContext, Exchange exchange)
        throws Exception {
        addParameters(dynamicQueryContext, exchange.getProperties());
        addParameters(dynamicQueryContext, exchange.getIn().getHeaders(), "in.headers.");
        dynamicQueryContext.setParameter("in.body", exchange.getIn().getBody());
        addParameters(dynamicQueryContext, getParameters());

        dynamicQueryContext.setParameter("exchange", exchange);
        if (exchange.hasOut() && exchange.getPattern().isOutCapable()) {
            dynamicQueryContext.setParameter("out.body", exchange.getOut().getBody());
            addParameters(dynamicQueryContext, exchange.getOut().getHeaders(), "out.headers.");
        }
    }
    
    protected void addParameters(DynamicQueryContext dynamicQueryContext, Map<String, Object> map) {
        addParameters(dynamicQueryContext, map, "");        
    }

    protected void addParameters(DynamicQueryContext dynamicQueryContext, Map<String, Object> map, String parameterPrefix) {
        Set<Map.Entry<String, Object>> propertyEntries = map.entrySet();
        for (Map.Entry<String, Object> entry : propertyEntries) {
            dynamicQueryContext.setParameter(parameterPrefix + entry.getKey(), entry.getValue());
        }
    }

    protected boolean matches(Exchange exchange, List results) {
        return ObjectHelper.matches(results);
    }

    /**
     * Initializes this builder - <b>Must be invoked before evaluation</b>.
     */
    protected synchronized void initialize(Exchange exchange) throws XPathException, IOException {
        // must use synchronized for concurrency issues and only let it initialize once
        if (!initialized.get()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Initializing XQueryBuilder " + this);
            }
            configuration = new Configuration();
            configuration.setHostLanguage(Configuration.XQUERY);
            configuration.setStripsWhiteSpace(isStripsAllWhiteSpace() ? Whitespace.ALL : Whitespace.IGNORABLE);

            staticQueryContext = new StaticQueryContext(getConfiguration());
            Set<Map.Entry<String, String>> entries = namespacePrefixes.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String prefix = entry.getKey();
                String uri = entry.getValue();
                staticQueryContext.declareNamespace(prefix, uri);
                staticQueryContext.setInheritNamespaces(true);
            }

            expression = createQueryExpression(staticQueryContext);

            initialized.set(true);
        }

        // let the configuration be accessible on the exchange as its shared for this evaluation
        // and can be needed by 3rd party type converters or other situations (camel-artixds)
        exchange.setProperty("CamelSaxonConfiguration", configuration);
    }

}
