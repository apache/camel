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
import java.io.FileNotFoundException;
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
import net.sf.saxon.trans.StaticError;
import net.sf.saxon.trans.XPathException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.BytesSource;
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Creates an XQuery builder
 *
 * @version $Revision$
 */
public abstract class XQueryBuilder implements Expression<Exchange>, Predicate<Exchange>, NamespaceAware, Processor {
    private static final transient Log LOG = LogFactory.getLog(XQueryBuilder.class);
    private Configuration configuration;
    private XQueryExpression expression;
    private StaticQueryContext staticQueryContext;
    private Map<String, Object> parameters = new HashMap<String, Object>();
    private Map<String, String> namespacePrefixes = new HashMap<String, String>();
    private XmlConverter converter = new XmlConverter();
    private ResultFormat resultsFormat = ResultFormat.DOM;
    private Properties properties = new Properties();
    private Class resultType;

    @Override
    public String toString() {
        return "XQuery[" + expression + "]";
    }

    public void process(Exchange exchange) throws Exception {
        Object body = evaluate(exchange);
        exchange.getOut(true).setBody(body);

        // propogate headers
        exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
    }

    public Object evaluate(Exchange exchange) {
        try {
            if (resultType != null) {
                if (resultType.equals(String.class)) {
                    return evaluateAsString(exchange);
                } else if (resultType.isAssignableFrom(Collection.class)) {
                    return evaluateAsList(exchange);
                } else if (resultType.isAssignableFrom(Node.class)) {
                    return evaluateAsDOM(exchange);
                } else {
                    // TODO figure out how to convert to the given type
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

    /**
     * Configures the namespace context from the given DOM element
     */
    public void setNamespaces(Map<String, String> namespaces) {
        namespacePrefixes.putAll(namespaces);
    }

    public List evaluateAsList(Exchange exchange) throws Exception {
        return getExpression().evaluate(createDynamicContext(exchange));
    }

    public Object evaluateAsStringSource(Exchange exchange) throws Exception {
        String text = evaluateAsString(exchange);
        return new StringSource(text);
    }

    public Object evaluateAsBytesSource(Exchange exchange) throws Exception {
        byte[] bytes = evaluateAsBytes(exchange);
        return new BytesSource(bytes);
    }

    public Node evaluateAsDOM(Exchange exchange) throws Exception {
        DOMResult result = new DOMResult();
        DynamicQueryContext context = createDynamicContext(exchange);
        XQueryExpression expression = getExpression();
        expression.pull(context, result, properties);
        return result.getNode();
    }

    public byte[] evaluateAsBytes(Exchange exchange) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Result result = new StreamResult(buffer);
        getExpression().pull(createDynamicContext(exchange), result, properties);
        byte[] bytes = buffer.toByteArray();
        return bytes;
    }

    public String evaluateAsString(Exchange exchange) throws Exception {
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
        try {
            List list = evaluateAsList(exchange);
            if (!matches(exchange, list)) {
                throw new AssertionError(this + " failed on " + exchange + " as evaluated: " + list);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // Static helper methods
    //-------------------------------------------------------------------------
    public static XQueryBuilder xquery(final String queryText) {
        return new XQueryBuilder() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext) throws XPathException {
                return staticQueryContext.compileQuery(queryText);
            }
        };
    }

    public static XQueryBuilder xquery(final Reader reader) {
        return new XQueryBuilder() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext) throws XPathException, IOException {
                return staticQueryContext.compileQuery(reader);
            }
        };
    }

    public static XQueryBuilder xquery(final InputStream in, final String characterSet) {
        return new XQueryBuilder() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext) throws XPathException, IOException {
                return staticQueryContext.compileQuery(in, characterSet);
            }
        };
    }

    public static XQueryBuilder xquery(File file, String characterSet) throws FileNotFoundException {
        return xquery(IOConverter.toInputStream(file), characterSet);
    }

    public static XQueryBuilder xquery(URL url, String characterSet) throws IOException {
        return xquery(IOConverter.toInputStream(url), characterSet);
    }

    public static XQueryBuilder xquery(File file) throws FileNotFoundException {
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

    // Properties
    // -------------------------------------------------------------------------

    public XQueryExpression getExpression() throws IOException, XPathException {
        if (expression == null) {
            expression = createQueryExpression(getStaticQueryContext());
            clearBuilderReferences();
        }
        return expression;
    }

    public Configuration getConfiguration() {
        if (configuration == null) {
            configuration = new Configuration();
            configuration.setHostLanguage(Configuration.XQUERY);
        }
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public StaticQueryContext getStaticQueryContext() {
        if (staticQueryContext == null) {
            staticQueryContext = new StaticQueryContext(getConfiguration());
            Set<Map.Entry<String, String>> entries = namespacePrefixes.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String prefix = entry.getKey();
                String uri = entry.getValue();
                staticQueryContext.declareNamespace(prefix, uri);
                staticQueryContext.setInheritNamespaces(true);
            }
        }
        return staticQueryContext;
    }

    public void setStaticQueryContext(StaticQueryContext staticQueryContext) {
        this.staticQueryContext = staticQueryContext;
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

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * A factory method to create the XQuery expression
     */
    protected abstract XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext) throws XPathException, IOException;

    /**
     * Creates a dynamic context for the given exchange
     */
    protected DynamicQueryContext createDynamicContext(Exchange exchange) throws Exception {
        Configuration config = getConfiguration();
        DynamicQueryContext dynamicQueryContext = new DynamicQueryContext(config);

        Message in = exchange.getIn();
        Item item = in.getBody(Item.class);
        Source source = null;
        if (item != null) {
            dynamicQueryContext.setContextItem(item);
        } else {
            source = in.getBody(Source.class);
            if (source == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No body available on exchange so using an empty document: " + exchange);
                }
                source = converter.toSource(converter.createDocument());
            }
            DocumentInfo doc = getStaticQueryContext().buildDocument(source);
            dynamicQueryContext.setContextItem(doc);
        }

        configureQuery(dynamicQueryContext, exchange);
        return dynamicQueryContext;
    }

    /**
     * Configures the dynamic context with exchange specific parameters
     *
     * @param dynamicQueryContext
     * @param exchange
     * @throws Exception
     */
    protected void configureQuery(DynamicQueryContext dynamicQueryContext, Exchange exchange) throws Exception {
        addParameters(dynamicQueryContext, exchange.getProperties());
        addParameters(dynamicQueryContext, exchange.getIn().getHeaders());
        addParameters(dynamicQueryContext, getParameters());

        dynamicQueryContext.setParameter("exchange", exchange);
        if (exchange.getPattern().isOutCapable()) {
            dynamicQueryContext.setParameter("out", exchange.getOut());
        }
    }

    protected void addParameters(DynamicQueryContext dynamicQueryContext, Map<String, Object> map) {
        Set<Map.Entry<String, Object>> propertyEntries = map.entrySet();
        for (Map.Entry<String, Object> entry : propertyEntries) {
            dynamicQueryContext.setParameter(entry.getKey(), entry.getValue());
        }
    }

    /**
     * To avoid keeping around any unnecessary objects after the expression has
     * been created lets nullify references here
     */
    protected void clearBuilderReferences() {
        staticQueryContext = null;
        configuration = null;
    }

    protected boolean matches(Exchange exchange, List results) {
        return ObjectHelper.matches(results);
    }
}
