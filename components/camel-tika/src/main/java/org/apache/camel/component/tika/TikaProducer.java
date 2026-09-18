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
package org.apache.camel.component.tika;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ExpandedTitleContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TikaProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TikaProducer.class);

    private static final HeaderFilterStrategy HEADER_FILTER_STRATEGY = createHeaderFilterStrategy();

    private final TikaConfiguration tikaConfiguration;

    private final TikaLoader tikaLoader;

    private final Parser parser;

    private final Detector detector;

    private final String encoding;

    public TikaProducer(TikaEndpoint endpoint) {
        this(endpoint, null);
    }

    public TikaProducer(TikaEndpoint endpoint, Parser parser) {
        super(endpoint);
        this.tikaConfiguration = endpoint.getTikaConfiguration();
        this.encoding = this.tikaConfiguration.getTikaParseOutputEncoding();
        try {
            this.tikaLoader = createTikaLoader(endpoint);
            this.detector = tikaLoader.loadDetectors();
            this.parser = parser == null ? tikaLoader.loadAutoDetectParser() : parser;
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private TikaLoader createTikaLoader(TikaEndpoint endpoint) throws TikaConfigException, IOException {
        TikaLoader configuredLoader = tikaConfiguration.getTikaLoader();
        if (configuredLoader != null) {
            return configuredLoader;
        }

        ClassLoader classLoader = endpoint.getCamelContext().getApplicationContextClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = TikaProducer.class.getClassLoader();
        }

        String configFile = tikaConfiguration.getTikaConfigFile();
        return configFile == null ? TikaLoader.loadDefault(classLoader) : TikaLoader.load(Path.of(configFile), classLoader);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        TikaOperation operation = this.tikaConfiguration.getOperation();
        Object result;
        switch (operation) {
            case detect:
                result = doDetect(exchange);
                break;
            case parse:
                result = doParse(exchange);
                break;
            default:
                throw new IllegalArgumentException(String.format("Unknown operation %s", tikaConfiguration.getOperation()));
        }
        // propagate headers
        exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
        // and set result
        exchange.getMessage().setBody(result);
    }

    private Object doDetect(Exchange exchange) throws IOException, TikaConfigException {
        MediaType result;
        Metadata metadata = new Metadata();
        ParseContext context = tikaLoader.loadParseContext();
        try (InputStream stream = exchange.getIn().getBody(InputStream.class);
             TikaInputStream inputStream = TikaInputStream.get(stream, metadata)) {
            result = this.detector.detect(inputStream, metadata, context);
            convertMetadataToHeaders(metadata, exchange);
        }
        return result.toString();
    }

    private Object doParse(Exchange exchange)
            throws TikaException, TikaConfigException, IOException, SAXException, TransformerConfigurationException {

        OutputStream result = new ByteArrayOutputStream();
        Metadata metadata = new Metadata();
        try (InputStream stream = exchange.getIn().getBody(InputStream.class);
             TikaInputStream inputStream = TikaInputStream.get(stream, metadata)) {
            ContentHandler contentHandler = getContentHandler(this.tikaConfiguration, result);
            ParseContext context = tikaLoader.loadParseContext();
            context.set(Parser.class, this.parser);
            this.parser.parse(inputStream, contentHandler, metadata, context);
            convertMetadataToHeaders(metadata, exchange);
        }
        return result;
    }

    private void convertMetadataToHeaders(Metadata metadata, Exchange exchange) {
        if (metadata != null) {
            for (String metaname : metadata.names()) {
                String[] values = metadata.getValues(metaname);
                Object value = values.length == 1 ? values[0] : values;
                // The names come out of the parsed document, so they are chosen by whoever produced it.
                // Filter them the same way a consumer filters names supplied by an external sender, so a
                // document cannot declare a metadata name that lands in the Camel-internal namespace.
                if (HEADER_FILTER_STRATEGY.applyFilterToExternalHeaders(metaname, value, exchange)) {
                    LOG.debug("Skipping parsed metadata {} as the name is in the Camel-internal namespace", metaname);
                    continue;
                }
                exchange.getIn().setHeader(metaname, value);
            }
        }
    }

    protected ContentHandler getContentHandler(TikaConfiguration configuration, OutputStream outputStream)
            throws TransformerConfigurationException, UnsupportedEncodingException {

        ContentHandler result = null;

        TikaParseOutputFormat outputFormat = configuration.getTikaParseOutputFormat();
        switch (outputFormat) {
            case xml:
                result = getTransformerHandler(outputStream, "xml", true);
                break;
            case text:
                result = new BodyContentHandler(new OutputStreamWriter(outputStream, this.encoding));
                break;
            case html:
                result = new ExpandedTitleContentHandler(getTransformerHandler(outputStream, "html", true));
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unknown format %s", tikaConfiguration.getTikaParseOutputFormat()));
        }
        return result;
    }

    private TransformerHandler getTransformerHandler(
            OutputStream output, String method,
            boolean prettyPrint)
            throws TransformerConfigurationException, UnsupportedEncodingException {
        SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, method);
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, prettyPrint ? "yes" : "no");
        if (this.encoding != null) {
            handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, this.encoding);
            handler.setResult(new StreamResult(new OutputStreamWriter(output, this.encoding)));
        } else {
            LOG.error("encoding is null");
            return null;
        }

        return handler;
    }

    private static HeaderFilterStrategy createHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy strategy = new DefaultHeaderFilterStrategy();
        // Match case-insensitively, and cover the fully qualified form as well as the Camel prefix
        strategy.setLowerCase(true);
        strategy.setInFilterStartsWith("Camel", "camel", "org.apache.camel.");
        return strategy;
    }

}
