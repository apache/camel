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
package org.apache.camel.dataformat.tagsoup;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.XMLConstants;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;

import org.w3c.dom.Node;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Parser;
import org.ccil.cowan.tagsoup.Schema;
import org.ccil.cowan.tagsoup.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dataformat for TidyMarkup (aka Well formed HTML in XML form.. may or may not
 * be XHTML) This dataformat is intended to convert bad HTML from a site (or
 * file) into a well formed HTML document which can then be sent to XSLT or
 * xpath'ed on.
 */
@Dataformat("tidyMarkup")
public class TidyMarkupDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    /*
     * Our Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(TidyMarkupDataFormat.class);

    private static final String NO = "no";

    private static final String YES = "yes";

    private static final String XML = "xml";

    /**
     * When returning a String, do we omit the XML declaration in the top.
     */
    private boolean omitXmlDeclaration;

    /**
     * String or Node to return
     */
    private Class<?> dataObjectType;

    /**
     * What is the default output format ?
     */
    private String method;

    /**
     * The Schema which we are parsing (default HTMLSchema)
     */
    private Schema parsingSchema;

    /**
     * User supplied Parser features
     * <p>
     * {@link http://home.ccil.org/~cowan/XML/tagsoup/#properties}
     * {@link http://www.saxproject.org/apidoc/org/xml/sax/package-summary.html}
     * </p>
     */
    private Map<String, Boolean> parserFeatures;

    /**
     * User supplied Parser properties
     * <p>
     * {@link http://home.ccil.org/~cowan/XML/tagsoup/#properties}
     * {@link http://www.saxproject.org/apidoc/org/xml/sax/package-summary.html}
     * </p>
     */
    private Map<String, Object> parserProperties;

    @Override
    public String getDataFormatName() {
        return "tidyMarkup";
    }

    /**
     * Unsupported operation. We cannot create ugly HTML.
     */
    @Override
    public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws Exception {
        throw new CamelException("Marshalling from Well Formed HTML to ugly HTML is not supported."
                + " Only unmarshal is supported");
    }

    /**
     * Unmarshal the data
     */
    @Override
    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {

        ObjectHelper.notNull(dataObjectType, "dataObjectType", this);

        if (dataObjectType.isAssignableFrom(Node.class)) {
            return asNodeTidyMarkup(inputStream);
        } else if (dataObjectType.isAssignableFrom(String.class)) {
            return asStringTidyMarkup(inputStream);
        } else {
            throw new IllegalArgumentException("The return type [" + dataObjectType.getCanonicalName()
                    + "] is unsupported");
        }
    }

    /**
     * Return the tidy markup as a string
     * 
     * @param inputStream
     * @return String of XML
     * @throws CamelException
     */
    public String asStringTidyMarkup(InputStream inputStream) throws CamelException {

        XMLReader parser = createTagSoupParser();
        StringWriter w = new StringWriter();
        parser.setContentHandler(createContentHandler(w));

        try {
            parser.parse(new InputSource(inputStream));
            return w.toString();

        } catch (Exception e) {
            throw new CamelException("Failed to convert the HTML to tidy Markup", e);
        } finally {
            try {
                inputStream.close();
            } catch (Exception e) {
                LOG.warn("Failed to close the inputStream");
            }
        }
    }

    /**
     * Return the HTML Markup as an {@link org.w3c.dom.Node}
     * 
     * @param inputStream
     *            The input Stream to convert
     * @return org.w3c.dom.Node The HTML Markup as a DOM Node
     * @throws CamelException
     */
    public Node asNodeTidyMarkup(InputStream inputStream) throws CamelException {
        XMLReader parser = createTagSoupParser();
        StringWriter w = new StringWriter();
        parser.setContentHandler(createContentHandler(w));

        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
            Transformer transformer = transformerFactory.newTransformer();
            DOMResult result = new DOMResult();
            transformer.transform(new SAXSource(parser, new InputSource(inputStream)), result);
            return result.getNode();
        } catch (Exception e) {
            throw new CamelException("Failed to convert the HTML to tidy Markup", e);
        }
    }

    /**
     * Create the tagSoup Parser
     */
    protected XMLReader createTagSoupParser() throws CamelException {
        XMLReader reader = new Parser();
        try {
            reader.setFeature(Parser.namespacesFeature, false);
            reader.setFeature(Parser.namespacePrefixesFeature, false);

            /*
             * set each parser feature that the user may have supplied.
             * http://www.saxproject.org/apidoc/org/xml/sax/package-summary.html
             * http://home.ccil.org/~cowan/XML/tagsoup/#properties
             */

            if (getParserFeatures() != null) {
                for (Entry<String, Boolean> e : getParserFeatures().entrySet()) {
                    reader.setFeature(e.getKey(), e.getValue());
                }
            }

            /*
             * set each parser feature that the user may have supplied. {@link
             * http://home.ccil.org/~cowan/XML/tagsoup/#properties}
             */

            if (getParserProperties() != null) {
                for (Entry<String, Object> e : getParserProperties().entrySet()) {
                    reader.setProperty(e.getKey(), e.getValue());
                }
            }

            /*
             * default the schema to HTML
             */
            if (this.getParsingSchema() != null) {
                reader.setProperty(Parser.schemaProperty, getParsingSchema());
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Problem configuring the parser", e);
        }
        return reader;
    }

    protected ContentHandler createContentHandler(Writer w) {
        XMLWriter xmlWriter = new XMLWriter(w);

        // we might need to expose more than these two but that is pretty good
        // for a default well formed Html generator
        if (getMethod() != null) {
            xmlWriter.setOutputProperty(XMLWriter.METHOD, getMethod());
        } else {
            xmlWriter.setOutputProperty(XMLWriter.METHOD, XML);
        }

        if (omitXmlDeclaration) {
            xmlWriter.setOutputProperty(XMLWriter.OMIT_XML_DECLARATION, YES);
        } else {
            xmlWriter.setOutputProperty(XMLWriter.OMIT_XML_DECLARATION, NO);
        }
        return xmlWriter;

    }

    public void setParsingSchema(Schema schema) {
        this.parsingSchema = schema;
    }

    public Schema getParsingSchema() {
        if (parsingSchema == null) {
            this.parsingSchema = new HTMLSchema();
        }
        return parsingSchema;
    }

    public boolean isOmitXmlDeclaration() {
        return omitXmlDeclaration;
    }

    public void setOmitXmlDeclaration(boolean omitXmlDeclaration) {
        this.omitXmlDeclaration = omitXmlDeclaration;
    }

    public void setParserFeatures(Map<String, Boolean> parserFeatures) {
        this.parserFeatures = parserFeatures;
    }

    public Map<String, Boolean> getParserFeatures() {
        return parserFeatures;
    }

    public void setParserProperties(Map<String, Object> parserProperties) {
        this.parserProperties = parserProperties;
    }

    public Map<String, Object> getParserProperties() {
        return parserProperties;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public Class<?> getDataObjectType() {
        return dataObjectType;
    }

    public void setDataObjectType(Class<?> dataObjectType) {
        this.dataObjectType = dataObjectType;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
