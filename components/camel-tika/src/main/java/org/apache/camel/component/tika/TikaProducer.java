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
import org.apache.camel.support.DefaultProducer;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ExpandedTitleContentHandler;

public class TikaProducer extends DefaultProducer {

    private final TikaConfiguration tikaConfiguration;

    private final Parser parser;

    private final Detector detector;

    private final String encoding;

    public TikaProducer(TikaEndpoint endpoint) {
        super(endpoint);
        this.tikaConfiguration = endpoint.getTikaConfiguration();
        this.encoding = this.tikaConfiguration.getTikaParseOutputEncoding();
        TikaConfig config = this.tikaConfiguration.getTikaConfig();
        this.parser = new AutoDetectParser(config);
        this.detector = config.getDetector();
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
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        // and set result
        exchange.getOut().setBody(result);
    }

    private Object doDetect(Exchange exchange) throws IOException {
        InputStream inputStream = exchange.getIn().getBody(InputStream.class);
        Metadata metadata = new Metadata();
        MediaType result = this.detector.detect(inputStream, metadata);
        convertMetadataToHeaders(metadata, exchange);
        return result.toString();
    }

    private Object doParse(Exchange exchange)
            throws TikaException, IOException, SAXException, TransformerConfigurationException {
        InputStream inputStream = exchange.getIn().getBody(InputStream.class);
        OutputStream result = new ByteArrayOutputStream();
        ContentHandler contentHandler = getContentHandler(this.tikaConfiguration, result);
        ParseContext context = new ParseContext();
        context.set(Parser.class, this.parser);
        Metadata metadata = new Metadata();
        this.parser.parse(inputStream, contentHandler, metadata, context);
        convertMetadataToHeaders(metadata, exchange);
        return result;
    }

    private void convertMetadataToHeaders(Metadata metadata, Exchange exchange) {
        if (metadata != null) {
            for (String metaname : metadata.names()) {
                String[] values = metadata.getValues(metaname);
                if (values.length == 1) {
                    exchange.getIn().setHeader(metaname, values[0]);
                } else {
                    exchange.getIn().setHeader(metaname, values);
                }
            }
        }
    }

    private ContentHandler getContentHandler(TikaConfiguration configuration, OutputStream outputStream)
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
            case textMain:
                result = new BoilerpipeContentHandler(new OutputStreamWriter(outputStream, this.encoding));
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

    private TransformerHandler getTransformerHandler(OutputStream output, String method,
                                                     boolean prettyPrint) throws TransformerConfigurationException, UnsupportedEncodingException {
        SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, method);
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, prettyPrint ? "yes" : "no");
        if (this.encoding != null) {
            handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, this.encoding);
        }
        handler.setResult(new StreamResult(new OutputStreamWriter(output, this.encoding)));
        return handler;
    }
}
