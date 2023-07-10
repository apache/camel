/*
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
package org.apache.camel.support.processor.validation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Node;
import org.w3c.dom.ls.LSResourceResolver;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExpectedBodyTypeException;
import org.apache.camel.RuntimeTransformException;
import org.apache.camel.TypeConverter;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.builder.xml.XMLConverterHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.processor.validation.SchemaReader.ACCESS_EXTERNAL_DTD;

/**
 * A processor which validates the XML version of the inbound message body against some schema either in XSD or RelaxNG
 */
public class ValidatingProcessor extends AsyncProcessorSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ValidatingProcessor.class);

    private final SchemaReader schemaReader;
    private ValidatorErrorHandler errorHandler = new DefaultValidationErrorHandler();
    private boolean useSharedSchema = true;
    private boolean failOnNullBody = true;
    private boolean failOnNullHeader = true;
    private String headerName;
    private final XMLConverterHelper converter = new XMLConverterHelper();

    public ValidatingProcessor() {
        schemaReader = new SchemaReader();
    }

    public ValidatingProcessor(SchemaReader schemaReader) {
        // schema reader can be a singleton per schema, therefore make reuse,
        // see ValidatorEndpoint and ValidatorProducer
        this.schemaReader = schemaReader;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            doProcess(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    protected void doProcess(Exchange exchange) throws Exception {
        Schema schema;
        if (isUseSharedSchema()) {
            schema = getSchema();
        } else {
            schema = createSchema();
        }

        Validator validator = schema.newValidator();
        // turn off access to external schema by default
        if (!Boolean.parseBoolean(exchange.getContext().getGlobalOptions().get(ACCESS_EXTERNAL_DTD))) {
            try {
                LOG.debug("Configuring Validator to not allow access to external DTD/Schema");
                validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (SAXException e) {
                LOG.warn(e.getMessage(), e);
            }
        }

        // the underlying input stream, which we need to close to avoid locking
        // files or
        // other resources
        Source source = null;
        InputStream is = null;
        try {
            Result result = null;
            // only convert to input stream if really needed
            if (isInputStreamNeeded(exchange)) {
                is = getContentToValidate(exchange, InputStream.class);
                if (is != null) {
                    source = getSource(exchange, is);
                }
            } else {
                Object content = getContentToValidate(exchange);
                if (content != null) {
                    source = getSource(exchange, content);
                }
            }

            if (shouldUseHeader()) {
                if (source == null && isFailOnNullHeader()) {
                    throw new NoXmlHeaderValidationException(exchange, headerName);
                }
            } else {
                if (source == null && isFailOnNullBody()) {
                    throw new NoXmlBodyValidationException(exchange);
                }
            }

            // CAMEL-7036 We don't need to set the result if the source is an
            // instance of StreamSource
            if (source instanceof DOMSource) {
                result = new DOMResult();
            } else if (source instanceof SAXSource) {
                result = new SAXResult();
            } else if (source instanceof StAXSource || source instanceof StreamSource) {
                result = null;
            }

            if (source != null) {
                // create a new errorHandler and set it on the validator
                // must be a local instance to avoid problems with concurrency
                // (to be thread safe)
                ValidatorErrorHandler handler = errorHandler.getClass().getDeclaredConstructor().newInstance();
                validator.setErrorHandler(handler);

                try {
                    LOG.trace("Validating {}", source);
                    validator.validate(source, result);
                    handler.handleErrors(exchange, schema, result);
                } catch (SAXParseException e) {
                    // can be thrown for non-well-formed XML
                    throw new SchemaValidationException(
                            exchange, schema, Collections.singletonList(e), Collections.<SAXParseException> emptyList(),
                            Collections.<SAXParseException> emptyList());
                }
            }
        } finally {
            IOHelper.close(is);
        }
    }

    private Object getContentToValidate(Exchange exchange) {
        if (shouldUseHeader()) {
            return exchange.getIn().getHeader(headerName);
        } else {
            return exchange.getIn().getBody();
        }
    }

    private <T> T getContentToValidate(Exchange exchange, Class<T> clazz) {
        if (shouldUseHeader()) {
            return exchange.getIn().getHeader(headerName, clazz);
        } else {
            return exchange.getIn().getBody(clazz);
        }
    }

    private boolean shouldUseHeader() {
        return headerName != null;
    }

    public void loadSchema() throws Exception {
        schemaReader.loadSchema();
    }

    // Properties
    // -----------------------------------------------------------------------

    public Schema getSchema() throws IOException, SAXException {
        return schemaReader.getSchema();
    }

    public void setSchema(Schema schema) {
        schemaReader.setSchema(schema);
    }

    public String getSchemaLanguage() {
        return schemaReader.getSchemaLanguage();
    }

    public void setSchemaLanguage(String schemaLanguage) {
        schemaReader.setSchemaLanguage(schemaLanguage);
    }

    public Source getSchemaSource() throws IOException {
        return schemaReader.getSchemaSource();
    }

    public void setSchemaSource(Source schemaSource) {
        schemaReader.setSchemaSource(schemaSource);
    }

    public URL getSchemaUrl() {
        return schemaReader.getSchemaUrl();
    }

    public void setSchemaUrl(URL schemaUrl) {
        schemaReader.setSchemaUrl(schemaUrl);
    }

    public File getSchemaFile() {
        return schemaReader.getSchemaFile();
    }

    public void setSchemaFile(File schemaFile) {
        schemaReader.setSchemaFile(schemaFile);
    }

    public byte[] getSchemaAsByteArray() {
        return schemaReader.getSchemaAsByteArray();
    }

    public void setSchemaAsByteArray(byte[] schemaAsByteArray) {
        schemaReader.setSchemaAsByteArray(schemaAsByteArray);
    }

    public SchemaFactory getSchemaFactory() {
        return schemaReader.getSchemaFactory();
    }

    public void setSchemaFactory(SchemaFactory schemaFactory) {
        schemaReader.setSchemaFactory(schemaFactory);
    }

    public ValidatorErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ValidatorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public boolean isUseSharedSchema() {
        return useSharedSchema;
    }

    public void setUseSharedSchema(boolean useSharedSchema) {
        this.useSharedSchema = useSharedSchema;
    }

    public LSResourceResolver getResourceResolver() {
        return schemaReader.getResourceResolver();
    }

    public void setResourceResolver(LSResourceResolver resourceResolver) {
        schemaReader.setResourceResolver(resourceResolver);
    }

    public boolean isFailOnNullBody() {
        return failOnNullBody;
    }

    public void setFailOnNullBody(boolean failOnNullBody) {
        this.failOnNullBody = failOnNullBody;
    }

    public boolean isFailOnNullHeader() {
        return failOnNullHeader;
    }

    public void setFailOnNullHeader(boolean failOnNullHeader) {
        this.failOnNullHeader = failOnNullHeader;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected SchemaFactory createSchemaFactory() {
        return schemaReader.createSchemaFactory();
    }

    protected Source createSchemaSource() throws IOException {
        return schemaReader.createSchemaSource();
    }

    protected Schema createSchema() throws SAXException, IOException {
        return schemaReader.createSchema();
    }

    /**
     * Checks whether we need an {@link InputStream} to access the message body or header.
     * <p/>
     * Depending on the content in the message body or header, we may not need to convert to {@link InputStream}.
     *
     * @param  exchange the current exchange
     * @return          <tt>true</tt> to convert to {@link InputStream} beforehand converting to {@link Source}
     *                  afterwards.
     */
    protected boolean isInputStreamNeeded(Exchange exchange) {
        Object content = getContentToValidate(exchange);
        if (content == null) {
            return false;
        }

        if (content instanceof InputStream) {
            return true;
        } else if (content instanceof Source) {
            return false;
        } else if (content instanceof String) {
            return false;
        } else if (content instanceof byte[]) {
            return false;
        } else if (content instanceof Node) {
            return false;
        } else if (exchange.getContext().getTypeConverterRegistry().lookup(Source.class, content.getClass()) != null) {
            // there is a direct and hopefully optimized converter to Source
            return false;
        }
        // yes an input stream is needed
        return true;
    }

    /**
     * Converts the inbound body or header to a {@link Source}, if it is <b>not</b> already a {@link Source}.
     * <p/>
     * This implementation will prefer to source in the following order:
     * <ul>
     * <li>DOM - DOM if explicit configured to use DOM</li>
     * <li>SAX - SAX as 2nd choice</li>
     * <li>Stream - Stream as 3rd choice</li>
     * <li>DOM - DOM as 4th choice</li>
     * </ul>
     */
    protected Source getSource(Exchange exchange, Object content) {
        // body or header may already be a source
        if (content instanceof Source) {
            return (Source) content;
        }
        Source source = null;
        if (content instanceof InputStream) {
            return new StreamSource((InputStream) content);
        }
        if (content != null) {
            TypeConverter tc = exchange.getContext().getTypeConverterRegistry().lookup(Source.class, content.getClass());
            if (tc != null) {
                source = tc.convertTo(Source.class, exchange, content);
            }
        }

        if (source == null) {
            // then try SAX
            source = exchange.getContext().getTypeConverter().tryConvertTo(SAXSource.class, exchange, content);
        }
        if (source == null) {
            // then try stream
            source = exchange.getContext().getTypeConverter().tryConvertTo(StreamSource.class, exchange, content);
        }
        if (source == null) {
            // and fallback to DOM
            source = exchange.getContext().getTypeConverter().tryConvertTo(DOMSource.class, exchange, content);
        }
        if (source == null) {
            if (isFailOnNullBody()) {
                throw new ExpectedBodyTypeException(exchange, Source.class);
            } else {
                try {
                    source = converter.toDOMSource(converter.createDocument());
                } catch (ParserConfigurationException | TransformerException e) {
                    throw new RuntimeTransformException(e);
                }
            }
        }
        return source;
    }

}
