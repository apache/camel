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

import org.apache.camel.Exchange;
import org.apache.camel.ExpectedBodyTypeException;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeTransformException;
import org.apache.camel.converter.jaxp.XmlConverter;
import static org.apache.camel.util.ObjectHelper.notNull;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates a <a href="http://activemq.apache.org/camel/processor.html">Processor</a>
 * which performs an XSLT transformation of the IN message body
 *
 * @version $Revision: 531854 $
 */
public class XsltBuilder implements Processor {
    private Map<String, Object> parameters = new HashMap<String, Object>();
    private XmlConverter converter = new XmlConverter();
    private Transformer transformer;
    private ResultHandler resultHandler = new StringResultHandler();
    private boolean failOnNullBody = true;

    public XsltBuilder() {
    }

    public XsltBuilder(Transformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public String toString() {
        return "XSLT[" + transformer + "]";
    }

    public synchronized void process(Exchange exchange) throws Exception {
        Transformer transformer = getTransformer();
        if (transformer == null) {
            throw new IllegalArgumentException("No transformer configured!");
        }
        configureTransformer(transformer, exchange);
        Source source = getSource(exchange);
        Result result = resultHandler.getResult();
        transformer.transform(source, result);
        resultHandler.setBody(exchange.getIn());
    }

    // Builder methods
    //-------------------------------------------------------------------------

    /**
     * Creates an XSLT processor using the given transformer instance
     */
    public static XsltBuilder xslt(Transformer transformer) {
        return new XsltBuilder(transformer);
    }

    /**
     * Creates an XSLT processor using the given XSLT source
     */
    public static XsltBuilder xslt(Source xslt) throws TransformerConfigurationException {
        notNull(xslt, "xslt");
        XsltBuilder answer = new XsltBuilder();
        answer.setTransformerSource(xslt);
        return answer;
    }

    /**
     * Creates an XSLT processor using the given XSLT source
     */
    public static XsltBuilder xslt(File xslt) throws TransformerConfigurationException {
        notNull(xslt, "xslt");
        return xslt(new StreamSource(xslt));
    }

    /**
     * Creates an XSLT processor using the given XSLT source
     */
    public static XsltBuilder xslt(URL xslt) throws TransformerConfigurationException, IOException {
        notNull(xslt, "xslt");
        return xslt(xslt.openStream());
    }

    /**
     * Creates an XSLT processor using the given XSLT source
     */
    public static XsltBuilder xslt(InputStream xslt) throws TransformerConfigurationException, IOException {
        notNull(xslt, "xslt");
        return xslt(new StreamSource(xslt));
    }

    /**
     * Sets the output as being a byte[]
     */
    public XsltBuilder outputBytes() {
        setResultHandler(new StreamResultHandler());
        return this;
    }

    /**
     * Sets the output as being a String
     */
    public XsltBuilder outputString() {
        setResultHandler(new StringResultHandler());
        return this;
    }

    /**
     * Sets the output as being a DOM
     */
    public XsltBuilder outputDOM() {
        setResultHandler(new DomResultHandler());
        return this;
    }

    public XsltBuilder parameter(String name, Object value) {
        parameters.put(name, value);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Transformer getTransformer() {
        return transformer;
    }

    public void setTransformer(Transformer transformer) {
        this.transformer = transformer;
    }

    public boolean isFailOnNullBody() {
        return failOnNullBody;
    }

    public void setFailOnNullBody(boolean failOnNullBody) {
        this.failOnNullBody = failOnNullBody;
    }

    public ResultHandler getResultHandler() {
        return resultHandler;
    }

    public void setResultHandler(ResultHandler resultHandler) {
        this.resultHandler = resultHandler;
    }

    public void setTransformerSource(Source source) throws TransformerConfigurationException {
        setTransformer(converter.getTransformerFactory().newTransformer(source));
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Converts the inbound body to a {@link Source}
     */
    protected Source getSource(Exchange exchange) {
        Message in = exchange.getIn();
        Source source = in.getBody(Source.class);
        if (source == null) {
            if (isFailOnNullBody()) {
                throw new ExpectedBodyTypeException(exchange, Source.class);
            }
            else {
                try {
                    source = converter.toSource(converter.createDocument());
                }
                catch (ParserConfigurationException e) {
                    throw new RuntimeTransformException(e);
                }
            }
        }
        return source;
    }

    /**
     * Configures the transformerwith exchange specific parameters
     */
    protected void configureTransformer(Transformer transformer, Exchange exchange) {
        transformer.clearParameters();

        addParameters(transformer, exchange.getProperties());
        addParameters(transformer, exchange.getIn().getHeaders());
        addParameters(transformer, getParameters());

        transformer.setParameter("exchange", exchange);
        transformer.setParameter("in", exchange.getIn());
        transformer.setParameter("out", exchange.getOut());
    }

    protected void addParameters(Transformer transformer, Map<String, Object> map) {
        Set<Map.Entry<String, Object>> propertyEntries = map.entrySet();
        for (Map.Entry<String, Object> entry : propertyEntries) {
            transformer.setParameter(entry.getKey(), entry.getValue());
        }
    }
}
