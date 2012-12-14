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
package org.apache.camel.processor.validation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collections;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Node;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.apache.camel.Exchange;
import org.apache.camel.ExpectedBodyTypeException;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeTransformException;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A processor which validates the XML version of the inbound message body
 * against some schema either in XSD or RelaxNG
 * 
 * @version
 */
public class ValidatingProcessor implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(ValidatingProcessor.class);
    private XmlConverter converter = new XmlConverter();
    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private Schema schema;
    private Source schemaSource;
    private SchemaFactory schemaFactory;
    private URL schemaUrl;
    private File schemaFile;
    private ValidatorErrorHandler errorHandler = new DefaultValidationErrorHandler();
    private boolean useDom;
    private boolean useSharedSchema = true;
    private LSResourceResolver resourceResolver;
    private boolean failOnNullBody = true;

    public void process(Exchange exchange) throws Exception {
        Schema schema;
        if (isUseSharedSchema()) {
            schema = getSchema();
        } else {
            schema = createSchema();
        }

        Validator validator = schema.newValidator();

        // the underlying input stream, which we need to close to avoid locking files or other resources
        Source source = null;
        InputStream is = null;
        try {
            Result result = null;
            // only convert to input stream if really needed
            if (isInputStreamNeeded(exchange)) {
                is = exchange.getIn().getBody(InputStream.class);
                if (is != null) {
                    source = getSource(exchange, is);
                }
            } else {
                Object body = exchange.getIn().getBody();
                if (body != null) {
                    source = getSource(exchange, body);
                }
            }

            if (source == null && isFailOnNullBody()) {
                throw new NoXmlBodyValidationException(exchange);
            }

            if (source instanceof DOMSource) {
                result = new DOMResult();
            } else if (source instanceof StreamSource) {
                result = new StreamResult(new StringWriter());
            } else if (source instanceof SAXSource) {
                result = new SAXResult();
            } else if (source instanceof StAXSource) {
                result = null;
            }

            if (source != null) {
                // create a new errorHandler and set it on the validator
                // must be a local instance to avoid problems with concurrency (to be
                // thread safe)
                ValidatorErrorHandler handler = errorHandler.getClass().newInstance();
                validator.setErrorHandler(handler);

                try {
                    LOG.trace("Validating {}", source);
                    validator.validate(source, result);
                    handler.handleErrors(exchange, schema, result);
                } catch (SAXParseException e) {
                    // can be thrown for non well formed XML
                    throw new SchemaValidationException(exchange, schema, Collections.singletonList(e),
                            Collections.<SAXParseException> emptyList(),
                            Collections.<SAXParseException> emptyList());
                }
            }
        } finally {
            IOHelper.close(is);
        }
    }

    public void loadSchema() throws Exception {
        // force loading of schema
        schema = createSchema();
    }

    // Properties
    // -----------------------------------------------------------------------

    public Schema getSchema() throws IOException, SAXException {
        if (schema == null) {
            schema = createSchema();
        }
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public String getSchemaLanguage() {
        return schemaLanguage;
    }

    public void setSchemaLanguage(String schemaLanguage) {
        this.schemaLanguage = schemaLanguage;
    }

    public Source getSchemaSource() throws IOException {
        if (schemaSource == null) {
            schemaSource = createSchemaSource();
        }
        return schemaSource;
    }

    public void setSchemaSource(Source schemaSource) {
        this.schemaSource = schemaSource;
    }

    public URL getSchemaUrl() {
        return schemaUrl;
    }

    public void setSchemaUrl(URL schemaUrl) {
        this.schemaUrl = schemaUrl;
    }

    public File getSchemaFile() {
        return schemaFile;
    }

    public void setSchemaFile(File schemaFile) {
        this.schemaFile = schemaFile;
    }

    public SchemaFactory getSchemaFactory() {
        if (schemaFactory == null) {
            schemaFactory = createSchemaFactory();
        }
        return schemaFactory;
    }

    public void setSchemaFactory(SchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

    public ValidatorErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ValidatorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Deprecated
    public boolean isUseDom() {
        return useDom;
    }

    /**
     * Sets whether DOMSource and DOMResult should be used.
     *
     * @param useDom true to use DOM otherwise
     */
    @Deprecated
    public void setUseDom(boolean useDom) {
        this.useDom = useDom;
    }

    public boolean isUseSharedSchema() {
        return useSharedSchema;
    }

    public void setUseSharedSchema(boolean useSharedSchema) {
        this.useSharedSchema = useSharedSchema;
    }

    public LSResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    public void setResourceResolver(LSResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public boolean isFailOnNullBody() {
        return failOnNullBody;
    }

    public void setFailOnNullBody(boolean failOnNullBody) {
        this.failOnNullBody = failOnNullBody;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected SchemaFactory createSchemaFactory() {
        SchemaFactory factory = SchemaFactory.newInstance(schemaLanguage);
        if (getResourceResolver() != null) {
            factory.setResourceResolver(getResourceResolver());
        }
        return factory;
    }

    protected Source createSchemaSource() throws IOException {
        throw new IllegalArgumentException("You must specify either a schema, schemaFile, schemaSource or schemaUrl property");
    }

    protected Schema createSchema() throws SAXException, IOException {
        SchemaFactory factory = getSchemaFactory();

        URL url = getSchemaUrl();
        if (url != null) {
            return factory.newSchema(url);
        }
        File file = getSchemaFile();
        if (file != null) {
            return factory.newSchema(file);
        }
        return factory.newSchema(getSchemaSource());
    }

    /**
     * Checks whether we need an {@link InputStream} to access the message body.
     * <p/>
     * Depending on the content in the message body, we may not need to convert
     * to {@link InputStream}.
     *
     * @param exchange the current exchange
     * @return <tt>true</tt> to convert to {@link InputStream} beforehand converting to {@link Source} afterwards.
     */
    protected boolean isInputStreamNeeded(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return false;
        }

        if (body instanceof InputStream) {
            return true;
        } else if (body instanceof Source) {
            return false;
        } else if (body instanceof String) {
            return false;
        } else if (body instanceof byte[]) {
            return false;
        } else if (body instanceof Node) {
            return false;
        } else if (exchange.getContext().getTypeConverterRegistry().lookup(Source.class, body.getClass()) != null) {
            //there is a direct and hopefully optimized converter to Source
            return false;
        }
        // yes an input stream is needed
        return true;
    }

    /**
     * Converts the inbound body to a {@link Source}, if the body is <b>not</b> already a {@link Source}.
     * <p/>
     * This implementation will prefer to source in the following order:
     * <ul>
     *   <li>DOM - DOM if explicit configured to use DOM</li>
     *   <li>SAX - SAX as 2nd choice</li>
     *   <li>Stream - Stream as 3rd choice</li>
     *   <li>DOM - DOM as 4th choice</li>
     * </ul>
     */
    protected Source getSource(Exchange exchange, Object body) {
        if (isUseDom()) {
            // force DOM
            return exchange.getContext().getTypeConverter().tryConvertTo(DOMSource.class, exchange, body);
        }

        // body may already be a source
        if (body instanceof Source) {
            return (Source) body;
        }
        Source source = null;
        if (body instanceof InputStream) {
            return new StreamSource((InputStream)body);
        }
        if (body != null) {
            TypeConverter tc = exchange.getContext().getTypeConverterRegistry().lookup(Source.class, body.getClass());
            if (tc != null) {
                source = tc.convertTo(Source.class, exchange, body);
            }
        }

        if (source == null) {
            // then try SAX
            source = exchange.getContext().getTypeConverter().tryConvertTo(SAXSource.class, exchange, body);
        }
        if (source == null) {
            // then try stream
            source = exchange.getContext().getTypeConverter().tryConvertTo(StreamSource.class, exchange, body);
        }
        if (source == null) {
            // and fallback to DOM
            source = exchange.getContext().getTypeConverter().tryConvertTo(DOMSource.class, exchange, body);
        }
        if (source == null) {
            if (isFailOnNullBody()) {
                throw new ExpectedBodyTypeException(exchange, Source.class);
            } else {
                try {
                    source = converter.toDOMSource(converter.createDocument());
                } catch (ParserConfigurationException e) {
                    throw new RuntimeTransformException(e);
                }
            }
        }
        return source;
    }

}