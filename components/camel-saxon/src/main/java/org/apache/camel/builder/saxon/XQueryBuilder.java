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
package org.apache.camel.builder.saxon;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.trans.XPathException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.BytesSource;
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.util.ObjectHelper;
import org.w3c.dom.Node;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Creates an XQuery builder
 * 
 * @version $Revision$
 */
public abstract class XQueryBuilder<E extends Exchange> implements Expression<E>, Predicate<E> {
    private Configuration configuration;
    private XQueryExpression expression;
    private StaticQueryContext staticQueryContext;
    private Map<String, Object> parameters = new HashMap<String, Object>();
    private XmlConverter converter = new XmlConverter();
    private ResultFormat resultsFormat = ResultFormat.DOM;
    private Properties properties = new Properties();

    @Override
    public String toString() {
        return "XQuery[" + expression + "]";
    }

    public Object evaluate(E exchange) {
        try {
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

    public List evaluateAsList(E exchange) throws Exception {
        return getExpression().evaluate(createDynamicContext(exchange));
    }

    public Object evaluateAsStringSource(E exchange) throws Exception {
        String text = evaluateAsString(exchange);
        return new StringSource(text);
    }

    public Object evaluateAsBytesSource(E exchange) throws Exception {
        byte[] bytes = evaluateAsBytes(exchange);
        return new BytesSource(bytes);
    }

    public Node evaluateAsDOM(E exchange) throws Exception {
        DOMResult result = new DOMResult();
        getExpression().pull(createDynamicContext(exchange), result, properties);
        return result.getNode();
    }

    public byte[] evaluateAsBytes(E exchange) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Result result = new StreamResult(buffer);
        getExpression().pull(createDynamicContext(exchange), result, properties);
        byte[] bytes = buffer.toByteArray();
        return bytes;
    }

    public String evaluateAsString(E exchange) throws Exception {
        StringWriter buffer = new StringWriter();
        Result result = new StreamResult(buffer);
        getExpression().pull(createDynamicContext(exchange), result, properties);
        String text = buffer.toString();
        return text;
    }

    public boolean matches(E exchange) {
        try {
            List list = evaluateAsList(exchange);
            return matches(exchange, list);
        } catch (Exception e) {
            throw new RuntimeExpressionException(e);
        }
    }

    public void assertMatches(String text, E exchange) throws AssertionError {
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
    public static <E extends Exchange> XQueryBuilder<E> xquery(final String queryText) {
        return new XQueryBuilder<E>() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext) throws XPathException {
                return staticQueryContext.compileQuery(queryText);
            }
        };
    }

    public static <E extends Exchange> XQueryBuilder<E> xquery(final Reader reader) {
        return new XQueryBuilder<E>() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext) throws XPathException, IOException {
                return staticQueryContext.compileQuery(reader);
            }
        };
    }

    public static <E extends Exchange> XQueryBuilder<E> xquery(final InputStream in, final String characterSet) {
        return new XQueryBuilder<E>() {
            protected XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext) throws XPathException, IOException {
                return staticQueryContext.compileQuery(in, characterSet);
            }
        };
    }

    public static <E extends Exchange> XQueryBuilder<E> xquery(File file, String characterSet) throws FileNotFoundException {
        return xquery(IOConverter.toInputStream(file), characterSet);
    }

    public static <E extends Exchange> XQueryBuilder<E> xquery(URL url, String characterSet) throws IOException {
        return xquery(IOConverter.toInputStream(url), characterSet);
    }

    public static <E extends Exchange> XQueryBuilder<E> xquery(File file) throws FileNotFoundException {
        return xquery(IOConverter.toInputStream(file), ObjectHelper.getDefaultCharacterSet());
    }

    public static <E extends Exchange> XQueryBuilder<E> xquery(URL url) throws IOException {
        return xquery(IOConverter.toInputStream(url), ObjectHelper.getDefaultCharacterSet());
    }


    // Fluent API
    // -------------------------------------------------------------------------
    public XQueryBuilder<E> asBytes() {
        setResultsFormat(ResultFormat.Bytes);
        return this;
    }

    public XQueryBuilder<E> asBytesSource() {
        setResultsFormat(ResultFormat.BytesSource);
        return this;
    }

    public XQueryBuilder<E> asDOM() {
        setResultsFormat(ResultFormat.DOM);
        return this;
    }

    public XQueryBuilder<E> asDOMSource() {
        setResultsFormat(ResultFormat.DOMSource);
        return this;
    }

    public XQueryBuilder<E> asList() {
        setResultsFormat(ResultFormat.List);
        return this;
    }

    public XQueryBuilder<E> asString() {
        setResultsFormat(ResultFormat.String);
        return this;
    }

    public XQueryBuilder<E> asStringSource() {
        setResultsFormat(ResultFormat.StringSource);
        return this;
    }

    public XQueryBuilder<E> parameter(String name, Object value) {
        parameters.put(name, value);
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

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * A factory method to create the XQuery expression
     */
    protected abstract XQueryExpression createQueryExpression(StaticQueryContext staticQueryContext) throws XPathException, IOException;

    /**
     * Creates a dynamic context for the given exchange
     */
    protected DynamicQueryContext createDynamicContext(E exchange) throws Exception {
        Configuration config = getConfiguration();
        DynamicQueryContext dynamicQueryContext = new DynamicQueryContext(config);

        Source source = exchange.getIn().getBody(Source.class);
        if (source == null) {
            source = converter.toSource(converter.createDocument());
        }

        DocumentInfo doc = getStaticQueryContext().buildDocument(source);
        dynamicQueryContext.setContextItem(doc);
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
        dynamicQueryContext.setParameter("in", exchange.getIn());
        dynamicQueryContext.setParameter("out", exchange.getOut());
    }

    protected void addParameters(DynamicQueryContext dynamicQueryContext, Map<String, Object> map) {
        Set<Map.Entry<String, Object>> propertyEntries = map.entrySet();
        for (Map.Entry<String, Object> entry : propertyEntries) {
            dynamicQueryContext.setParameter(entry.getKey(), entry.getValue());
        }
    }

    /**
     * To avoid keeping around any unnecessary objects after the expresion has
     * been created lets nullify references here
     */
    protected void clearBuilderReferences() {
        staticQueryContext = null;
        configuration = null;
    }

    protected boolean matches(E exchange, List results) {
        return ObjectHelper.matches(results);
    }
}
