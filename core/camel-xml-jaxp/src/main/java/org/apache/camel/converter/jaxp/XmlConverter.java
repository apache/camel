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
package org.apache.camel.converter.jaxp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.xml.BytesSource;
import org.apache.camel.util.xml.StringSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class to transform to and from various JAXB types such as {@link Source} and {@link Document}
 */
@Converter(generateBulkLoader = true)
public class XmlConverter {

    public static final String OUTPUT_PROPERTIES_PREFIX = "org.apache.camel.xmlconverter.output.";
    public static final String DOCUMENT_BUILDER_FACTORY_FEATURE
            = "org.apache.camel.xmlconverter.documentBuilderFactory.feature";
    public static final String defaultCharset = ObjectHelper.getSystemProperty(Exchange.DEFAULT_CHARSET_PROPERTY, "UTF-8");

    private static final String JDK_FALLBACK_TRANSFORMER_FACTORY
            = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";
    private static final Logger LOG = LoggerFactory.getLogger(XmlConverter.class);
    private static final ErrorHandler DOCUMENT_BUILDER_LOGGING_ERROR_HANDLER = new DocumentBuilderLoggingErrorHandler();

    private volatile DocumentBuilderFactory documentBuilderFactory;
    private volatile TransformerFactory transformerFactory;
    private volatile XMLReaderPool xmlReaderPool;

    public XmlConverter() {
    }

    public XmlConverter(DocumentBuilderFactory documentBuilderFactory) {
        this.documentBuilderFactory = documentBuilderFactory;
    }

    /**
     * Returns the default set of output properties for conversions.
     */
    public Properties defaultOutputProperties() {
        Properties properties = new Properties();
        properties.put(OutputKeys.ENCODING, defaultCharset);
        properties.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
        return properties;
    }

    /**
     * Converts the given input Source into the required result
     */
    public void toResult(Source source, Result result) throws TransformerException {
        toResult(source, result, defaultOutputProperties());
    }

    /**
     * Converts the given input Source into the required result
     */
    public void toResult(Source source, Result result, Properties outputProperties) throws TransformerException {
        if (source == null) {
            return;
        }

        Transformer transformer = createTransformer();
        if (transformer == null) {
            throw new TransformerException("Could not create a transformer - JAXP is misconfigured!");
        }
        transformer.setOutputProperties(outputProperties);
        transformer.transform(source, result);
    }

    /**
     * Converts the given string to a QName.
     */
    @Converter(order = 1)
    public QName toQName(String str) {
        return QName.valueOf(str);
    }

    /**
     * Converts the given NodeList to a boolean
     */
    @Converter(order = 2)
    public Boolean toBoolean(NodeList list) {
        return list.getLength() > 0;
    }

    /**
     * Converts the given byte[] to a Source
     */
    @Converter(order = 3)
    public BytesSource toBytesSource(byte[] data) {
        return new BytesSource(data);
    }

    /**
     * Converts the given String to a Source
     */
    @Converter(order = 4)
    public StringSource toStringSource(String data) {
        return new StringSource(data);
    }

    /**
     * Converts the given Document to a DOMSource
     */
    @Converter(order = 5)
    public DOMSource toDOMSource(Document document) {
        return new DOMSource(document);
    }

    /**
     * Converts the given Node to a Source
     */
    @Converter(order = 6)
    public DOMSource toDOMSource(Node node) throws ParserConfigurationException, TransformerException {
        Document document = toDOMDocument(node);
        return new DOMSource(document);
    }

    /**
     * Converts the given String to a Source
     */
    @Converter(order = 7)
    public Source toSource(String data) {
        return new StringSource(data);
    }

    @Converter(order = 8)
    public Source toSource(byte[] in) {
        return new BytesSource(in);
    }

    /**
     * Converts the given Document to a Source
     */
    @Converter(order = 9)
    public Source toSource(Document document) {
        return new DOMSource(document);
    }

    @Converter(order = 10)
    public Source toSource(StreamCache cache, Exchange exchange) {
        byte[] arr = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, cache);
        return toSource(arr);
    }

    /**
     * Converts the given input Source into text
     */
    @Converter(order = 11)
    public String toString(Source source, Exchange exchange) throws TransformerException {
        if (source == null) {
            return null;
        } else if (source instanceof StringSource) {
            return ((StringSource) source).getText();
        } else if (source instanceof BytesSource) {
            return new String(((BytesSource) source).getData());
        } else {
            StringWriter buffer = new StringWriter();
            if (exchange != null) {
                // check the camelContext properties first
                Properties properties
                        = CamelContextHelper.getCamelPropertiesWithPrefix(OUTPUT_PROPERTIES_PREFIX, exchange.getContext());
                if (!properties.isEmpty()) {
                    toResult(source, new StreamResult(buffer), properties);
                    return buffer.toString();
                }
            }
            // using the old way to deal with it
            toResult(source, new StreamResult(buffer));
            return buffer.toString();
        }
    }

    /**
     * Converts the given input Source into bytes
     */
    @Converter(order = 12)
    public byte[] toByteArray(Source source, Exchange exchange) throws TransformerException {
        if (source instanceof BytesSource) {
            return ((BytesSource) source).getData();
        } else {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (exchange != null) {
                // check the camelContext properties first
                Properties properties = CamelContextHelper.getCamelPropertiesWithPrefix(OUTPUT_PROPERTIES_PREFIX,
                        exchange.getContext());
                if (!properties.isEmpty()) {
                    toResult(source, new StreamResult(buffer), properties);
                    return buffer.toByteArray();
                }
            }
            // using the old way to deal with it
            toResult(source, new StreamResult(buffer));
            return buffer.toByteArray();
        }
    }

    /**
     * Converts the source instance to a {@link DOMSource} or returns null if the conversion is not supported (making it
     * easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 13)
    public DOMSource toDOMSource(String text)
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        Source source = toSource(text);
        return toDOMSourceFromStream((StreamSource) source, null);
    }

    /**
     * Converts the source instance to a {@link DOMSource} or returns null if the conversion is not supported (making it
     * easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 14)
    public DOMSource toDOMSource(byte[] bytes) throws IOException, SAXException, ParserConfigurationException {
        InputStream is = new ByteArrayInputStream(bytes);
        try {
            return toDOMSource(is, null);
        } finally {
            IOHelper.close(is);
        }
    }

    /**
     * Converts the source instance to a {@link SAXSource} or returns null if the conversion is not supported (making it
     * easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 15)
    public SAXSource toSAXSource(String source, Exchange exchange) throws SAXException, TransformerException {
        return toSAXSource(toSource(source), exchange);
    }

    /**
     * Converts the source instance to a {@link StAXSource} or returns null if the conversion is not supported (making
     * it easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 16)
    public StAXSource toStAXSource(String source, Exchange exchange) throws XMLStreamException {
        XMLStreamReader r = new StaxConverter().createXMLStreamReader(new StringReader(source));
        return new StAXSource(r);
    }

    /**
     * Converts the source instance to a {@link StAXSource} or returns null if the conversion is not supported (making
     * it easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 17)
    public StAXSource toStAXSource(byte[] in, Exchange exchange) throws XMLStreamException {
        XMLStreamReader r = new StaxConverter().createXMLStreamReader(new ByteArrayInputStream(in), exchange);
        return new StAXSource(r);
    }

    /**
     * Converts the source instance to a {@link SAXSource} or returns null if the conversion is not supported (making it
     * easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 18)
    public SAXSource toSAXSource(InputStream source, Exchange exchange) throws SAXException, TransformerException {
        return toSAXSource(toStreamSource(source), exchange);
    }

    /**
     * Converts the source instance to a {@link SAXSource} or returns null if the conversion is not supported (making it
     * easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 19)
    public SAXSource toSAXSource(byte[] in, Exchange exchange) throws SAXException, TransformerException {
        return toSAXSource(toStreamSource(in, exchange), exchange);
    }

    /**
     * Converts the source instance to a {@link StAXSource} or returns null if the conversion is not supported (making
     * it easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 20)
    public StAXSource toStAXSource(InputStream source, Exchange exchange) throws XMLStreamException {
        XMLStreamReader r = new StaxConverter().createXMLStreamReader(source, exchange);
        return new StAXSource(r);
    }

    /**
     * Converts the source instance to a {@link SAXSource} or returns null if the conversion is not supported (making it
     * easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 21)
    public SAXSource toSAXSource(File file, Exchange exchange) throws IOException, SAXException, TransformerException {
        InputStream is = IOHelper.buffered(new FileInputStream(file));
        return toSAXSource(is, exchange);
    }

    /**
     * Converts the source instance to a {@link StAXSource} or returns null if the conversion is not supported (making
     * it easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 22)
    public StAXSource toStAXSource(File file, Exchange exchange) throws FileNotFoundException, XMLStreamException {
        InputStream is = IOHelper.buffered(new FileInputStream(file));
        XMLStreamReader r = new StaxConverter().createXMLStreamReader(is, exchange);
        return new StAXSource(r);
    }

    @Converter(order = 23)
    public StreamSource toStreamSource(String in) {
        return new StreamSource(new ByteArrayInputStream(in.getBytes()));
    }

    @Converter(order = 24)
    public StreamSource toStreamSource(InputStream in) {
        return new StreamSource(in);
    }

    @Converter(order = 25)
    public StreamSource toStreamSource(Reader in) {
        return new StreamSource(in);
    }

    @Converter(order = 26)
    public StreamSource toStreamSource(File in) {
        return new StreamSource(in);
    }

    @Converter(order = 27)
    public StreamSource toStreamSource(byte[] in, Exchange exchange) {
        InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, in);
        return new StreamSource(is);
    }

    @Converter(order = 28)
    public StreamSource toStreamSource(ByteBuffer in, Exchange exchange) {
        InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, in);
        return new StreamSource(is);
    }

    @Converter(order = 29)
    public StreamSource toStreamSourceFromSAX(SAXSource source, Exchange exchange) throws TransformerException {
        InputSource inputSource = source.getInputSource();
        if (inputSource != null) {
            if (inputSource.getCharacterStream() != null) {
                return new StreamSource(inputSource.getCharacterStream());
            }
            if (inputSource.getByteStream() != null) {
                return new StreamSource(inputSource.getByteStream());
            }
        }
        String result = toString(source, exchange);
        return new StringSource(result);
    }

    @Converter(order = 30)
    public StreamSource toStreamSourceFromDOM(DOMSource source, Exchange exchange) throws TransformerException {
        String result = toString(source, exchange);
        return new StringSource(result);
    }

    @Converter(order = 31)
    public StreamSource toStreamSourceFromStAX(StAXSource source, Exchange exchange) throws TransformerException {
        String result = toString(source, exchange);
        return new StringSource(result);
    }

    @Converter(order = 32)
    public SAXSource toSAXSourceFromStream(StreamSource source, Exchange exchange) throws SAXException {
        InputSource inputSource;
        if (source.getReader() != null) {
            inputSource = new InputSource(source.getReader());
        } else {
            inputSource = new InputSource(source.getInputStream());
        }
        inputSource.setSystemId(source.getSystemId());
        inputSource.setPublicId(source.getPublicId());

        XMLReader xmlReader = null;
        try {
            // use the SAXPaserFactory which is set from exchange
            if (exchange != null) {
                SAXParserFactory sfactory = exchange.getProperty(Exchange.SAXPARSER_FACTORY, SAXParserFactory.class);
                if (sfactory != null) {
                    if (!sfactory.isNamespaceAware()) {
                        sfactory.setNamespaceAware(true);
                    }
                    xmlReader = sfactory.newSAXParser().getXMLReader();
                }
            }
            if (xmlReader == null) {
                if (xmlReaderPool == null) {
                    xmlReaderPool = new XMLReaderPool(createSAXParserFactory());
                }
                xmlReader = xmlReaderPool.createXMLReader();
            }
        } catch (Exception ex) {
            LOG.warn("Cannot create the SAXParser XMLReader, due to {}", ex.getMessage(), ex);
        }
        return new SAXSource(xmlReader, inputSource);
    }

    @Converter(order = 33)
    public Reader toReader(StreamSource source) {
        Reader r = source.getReader();
        if (r == null) {
            r = new InputStreamReader(source.getInputStream());
        }
        return r;
    }

    @Converter(order = 34)
    public Reader toReaderFromSource(Source src, Exchange exchange) throws TransformerException {
        StreamSource stSrc = toStreamSource(src, exchange);
        Reader r = stSrc.getReader();
        if (r == null) {
            r = new InputStreamReader(stSrc.getInputStream());
        }
        return r;
    }

    @Converter(order = 35)
    public DOMSource toDOMSource(StreamCache cache, Exchange exchange)
            throws ParserConfigurationException, IOException, SAXException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        cache.writeTo(bos);
        return toDOMSource(new ByteArrayInputStream(bos.toByteArray()), exchange);
    }

    @Converter(order = 36)
    public DOMSource toDOMSource(InputStream is, Exchange exchange)
            throws ParserConfigurationException, IOException, SAXException {
        InputSource source = new InputSource(is);
        String systemId = source.getSystemId();
        DocumentBuilder builder = createDocumentBuilder(getDocumentBuilderFactory(exchange));
        Document document = builder.parse(source);
        return new DOMSource(document, systemId);
    }

    @Converter(order = 37)
    public DOMSource toDOMSource(File file, Exchange exchange) throws ParserConfigurationException, IOException, SAXException {
        InputStream is = IOHelper.buffered(new FileInputStream(file));
        return toDOMSource(is, exchange);
    }

    @Converter(order = 38)
    public DOMSource toDOMSourceFromStream(StreamSource source, Exchange exchange)
            throws ParserConfigurationException, IOException, SAXException {
        Document document;
        String systemId = source.getSystemId();

        DocumentBuilder builder = createDocumentBuilder(getDocumentBuilderFactory(exchange));
        Reader reader = source.getReader();
        if (reader != null) {
            document = builder.parse(new InputSource(reader));
        } else {
            InputStream inputStream = source.getInputStream();
            if (inputStream != null) {
                InputSource inputsource = new InputSource(inputStream);
                inputsource.setSystemId(systemId);
                document = builder.parse(inputsource);
            } else {
                throw new IOException("No input stream or reader available on StreamSource: " + source);
            }
        }
        return new DOMSource(document, systemId);
    }

    @Converter(order = 39)
    public SAXSource toSAXSourceFromDOM(DOMSource source, Exchange exchange) throws TransformerException {
        String str = toString(source, exchange);
        StringReader reader = new StringReader(str);
        return new SAXSource(new InputSource(reader));
    }

    @Converter(order = 40)
    public SAXSource toSAXSourceFromStAX(StAXSource source, Exchange exchange) throws TransformerException {
        String str = toString(source, exchange);
        StringReader reader = new StringReader(str);
        return new SAXSource(new InputSource(reader));
    }

    @Converter(order = 41)
    public DOMSource toDOMSourceFromSAX(SAXSource source)
            throws TransformerException {
        return new DOMSource(toDOMNodeFromSAX(source));
    }

    @Converter(order = 42)
    public DOMSource toDOMSourceFromStAX(StAXSource source)
            throws TransformerException {
        return new DOMSource(toDOMNodeFromStAX(source));
    }

    @Converter(order = 43)
    public Node toDOMNodeFromSAX(SAXSource source)
            throws TransformerException {
        DOMResult result = new DOMResult();
        toResult(source, result);
        return result.getNode();
    }

    @Converter(order = 44)
    public Node toDOMNodeFromStAX(StAXSource source)
            throws TransformerException {
        DOMResult result = new DOMResult();
        toResult(source, result);
        return result.getNode();
    }

    /**
     * Convert a NodeList consisting of just 1 node to a DOM Node.
     *
     * @param  nl the NodeList
     * @return    the DOM Node
     */
    @Converter(order = 45, allowNull = true)
    public Node toDOMNodeFromSingleNodeList(NodeList nl) {
        return nl.getLength() == 1 ? nl.item(0) : null;
    }

    /**
     * Create a DOM document from the given Node.
     *
     * If the node is a document, just cast it, if the node is an root element, retrieve its owner element or create a
     * new document and import the node.
     */
    @Converter(order = 46)
    public Document toDOMDocument(final Node node) throws ParserConfigurationException, TransformerException {
        ObjectHelper.notNull(node, "node");

        // If the node is the document, just cast it
        if (node instanceof Document) {
            return (Document) node;
            // If the node is an element
        } else if (node instanceof Element elem) {
            // If this is the root element, return its owner document
            if (elem.getOwnerDocument().getDocumentElement() == elem) {
                return elem.getOwnerDocument();
                // else, create a new doc and copy the element inside it
            } else {
                Document doc = createDocument();
                // import node must not occur concurrent on the same node (must be its owner)
                // so we need to synchronize on it
                synchronized (node.getOwnerDocument()) {
                    doc.appendChild(doc.importNode(node, true));
                }
                return doc;
            }
            // other element types are not handled
        } else {
            throw new TransformerException("Unable to convert DOM node to a Document: " + node);
        }
    }

    /**
     * Converts the given Source into a W3C DOM node
     */
    @Converter(order = 47, allowNull = true)
    public Node toDOMNode(Source source) throws TransformerException, ParserConfigurationException, IOException, SAXException {
        DOMSource domSrc = toDOMSource(source, null);
        return domSrc != null ? domSrc.getNode() : null;
    }

    /**
     * Create a DOM element from the given source.
     */
    @Converter(order = 48)
    public Element toDOMElement(Source source)
            throws TransformerException, ParserConfigurationException, IOException, SAXException {
        Node node = toDOMNode(source);
        return toDOMElement(node);
    }

    /**
     * Create a DOM element from the DOM node. Simply cast if the node is an Element, or return the root element if it
     * is a Document.
     */
    @Converter(order = 49)
    public Element toDOMElement(Node node) throws TransformerException {
        // If the node is an document, return the root element
        if (node instanceof Document) {
            return ((Document) node).getDocumentElement();
            // If the node is an element, just cast it
        } else if (node instanceof Element) {
            return (Element) node;
            // Other node types are not handled
        } else {
            throw new TransformerException("Unable to convert DOM node to an Element");
        }
    }

    /**
     * Converts the given data to a DOM document
     *
     * @param  data     is the data to be parsed
     * @param  exchange is the exchange to be used when calling the converter
     * @return          the parsed document
     */
    @Converter(order = 50)
    public Document toDOMDocument(byte[] data, Exchange exchange)
            throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilder documentBuilder = createDocumentBuilder(getDocumentBuilderFactory(exchange));
        return documentBuilder.parse(new ByteArrayInputStream(data));
    }

    @Converter(order = 51)
    public Document toDOMDocument(StreamCache cache, Exchange exchange)
            throws IOException, SAXException, ParserConfigurationException {
        InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, cache);
        return toDOMDocument(is, exchange);
    }

    /**
     * Converts the given {@link InputStream} to a DOM document
     *
     * @param  in       is the data to be parsed
     * @param  exchange is the exchange to be used when calling the converter
     * @return          the parsed document
     */
    @Converter(order = 52)
    public Document toDOMDocument(InputStream in, Exchange exchange)
            throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilder documentBuilder = createDocumentBuilder(getDocumentBuilderFactory(exchange));
        if (in instanceof IOHelper.EncodingInputStream encIn) {
            // DocumentBuilder detects encoding from XML declaration, so we need to
            // revert the converted encoding for the input stream
            return documentBuilder.parse(encIn.toOriginalInputStream());
        } else {
            return documentBuilder.parse(in);
        }
    }

    /**
     * Converts the given {@link Reader} to a DOM document
     *
     * @param  in       is the data to be parsed
     * @param  exchange is the exchange to be used when calling the converter
     * @return          the parsed document
     */
    @Converter(order = 53)
    public Document toDOMDocument(Reader in, Exchange exchange) throws IOException, SAXException, ParserConfigurationException {
        return toDOMDocument(new InputSource(in), exchange);
    }

    /**
     * Converts the given {@link InputSource} to a DOM document
     *
     * @param  in       is the data to be parsed
     * @param  exchange is the exchange to be used when calling the converter
     * @return          the parsed document
     */
    @Converter(order = 54)
    public Document toDOMDocument(InputSource in, Exchange exchange)
            throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilder documentBuilder = createDocumentBuilder(getDocumentBuilderFactory(exchange));
        return documentBuilder.parse(in);
    }

    /**
     * Converts the given {@link String} to a DOM document
     *
     * @param  text     is the data to be parsed
     * @param  exchange is the exchange to be used when calling the converter
     * @return          the parsed document
     */
    @Converter(order = 55)
    public Document toDOMDocument(String text, Exchange exchange)
            throws IOException, SAXException, ParserConfigurationException {
        return toDOMDocument(new StringReader(text), exchange);
    }

    /**
     * Converts the given {@link File} to a DOM document
     *
     * @param  file     is the data to be parsed
     * @param  exchange is the exchange to be used when calling the converter
     * @return          the parsed document
     */
    @Converter(order = 56)
    public Document toDOMDocument(File file, Exchange exchange) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilder documentBuilder = createDocumentBuilder(getDocumentBuilderFactory(exchange));
        return documentBuilder.parse(file);
    }

    /**
     * Create a DOM document from the given source.
     */
    @Converter(order = 57)
    public Document toDOMDocument(Source source)
            throws TransformerException, ParserConfigurationException, IOException, SAXException {
        Node node = toDOMNode(source);
        if (node != null) {
            return toDOMDocument(node);
        } else {
            return null;
        }
    }

    /**
     * Convert a NodeList consisting of just 1 node to a DOM Document. Cannot convert NodeList with length > 1 because
     * they require a root node.
     *
     * @param  nl the NodeList
     * @return    the DOM Document
     */
    @Converter(order = 58, allowNull = true)
    public Document toDOMDocumentFromSingleNodeList(NodeList nl) throws ParserConfigurationException, TransformerException {
        if (nl.getLength() == 1) {
            return toDOMDocument(nl.item(0));
        } else if (nl instanceof Node) {
            // as XML parsers may often have nodes that implement both Node and NodeList then the type converter lookup
            // may lookup either a type converter from NodeList or Node. So let's fallback and try with Node
            return toDOMDocument((Node) nl);
        } else {
            return null;
        }
    }

    @Converter(order = 59)
    public InputStream toInputStream(DOMSource source, Exchange exchange) throws TransformerException {
        return new ByteArrayInputStream(toByteArray(source, exchange));
    }

    @Converter(order = 60)
    public InputStream toInputStream(Document dom, Exchange exchange) throws TransformerException {
        return toInputStream(new DOMSource(dom), exchange);
    }

    @Converter(order = 61)
    public InputSource toInputSource(InputStream is, Exchange exchange) {
        return new InputSource(is);
    }

    @Converter(order = 62)
    public InputSource toInputSource(File file, Exchange exchange) throws FileNotFoundException {
        InputStream is = IOHelper.buffered(new FileInputStream(file));
        return new InputSource(is);
    }

    /**
     * Converts the source instance to a {@link DOMSource} or returns null if the conversion is not supported (making it
     * easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 63)
    public DOMSource toDOMSource(Source source, Exchange exchange)
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        if (source instanceof DOMSource) {
            return (DOMSource) source;
        } else if (source instanceof SAXSource) {
            return toDOMSourceFromSAX((SAXSource) source);
        } else if (source instanceof StreamSource) {
            return toDOMSourceFromStream((StreamSource) source, exchange);
        } else if (source instanceof StAXSource) {
            return toDOMSourceFromStAX((StAXSource) source);
        } else {
            return null;
        }
    }

    /**
     * Converts the source instance to a {@link SAXSource} or returns null if the conversion is not supported (making it
     * easy to derive from this class to add new kinds of conversion).
     */
    @Converter(order = 64)
    public SAXSource toSAXSource(Source source, Exchange exchange) throws SAXException, TransformerException {
        if (source instanceof SAXSource) {
            return (SAXSource) source;
        } else if (source instanceof DOMSource) {
            return toSAXSourceFromDOM((DOMSource) source, exchange);
        } else if (source instanceof StreamSource) {
            return toSAXSourceFromStream((StreamSource) source, exchange);
        } else if (source instanceof StAXSource) {
            return toSAXSourceFromStAX((StAXSource) source, exchange);
        } else {
            return null;
        }
    }

    @Converter(order = 65)
    public StreamSource toStreamSource(Source source, Exchange exchange) throws TransformerException {
        if (source instanceof StreamSource) {
            return (StreamSource) source;
        } else if (source instanceof DOMSource) {
            return toStreamSourceFromDOM((DOMSource) source, exchange);
        } else if (source instanceof SAXSource) {
            return toStreamSourceFromSAX((SAXSource) source, exchange);
        } else if (source instanceof StAXSource) {
            return toStreamSourceFromStAX((StAXSource) source, exchange);
        } else {
            return null;
        }
    }

    @Converter(order = 66)
    public InputStream toInputStream(StreamSource source) throws IOException {
        InputStream is = source.getInputStream();
        if (is == null) {
            Reader r = source.getReader();
            String s = IOHelper.toString(r);
            is = new ByteArrayInputStream(s.getBytes());
        }
        return is;
    }

    /**
     * Converts the given Document to into text
     *
     * @param  document      The document to convert
     * @param  outputOptions The {@link OutputKeys} properties to control various aspects of the XML output
     * @return               The string representation of the document
     */
    public String toStringFromDocument(Document document, Properties outputOptions) throws TransformerException {
        if (document == null) {
            return null;
        }

        DOMSource source = new DOMSource(document);
        StringWriter buffer = new StringWriter();
        toResult(source, new StreamResult(buffer), outputOptions);
        return buffer.toString();
    }

    // Properties
    //-------------------------------------------------------------------------

    public DocumentBuilderFactory getDocumentBuilderFactory() {
        if (documentBuilderFactory == null) {
            documentBuilderFactory = createDocumentBuilderFactory();
        }
        return documentBuilderFactory;
    }

    public void setDocumentBuilderFactory(DocumentBuilderFactory documentBuilderFactory) {
        this.documentBuilderFactory = documentBuilderFactory;
    }

    public TransformerFactory getTransformerFactory() {
        if (transformerFactory == null) {
            transformerFactory = createTransformerFactory();
        }
        return transformerFactory;
    }

    public void setTransformerFactory(TransformerFactory transformerFactory) {
        if (transformerFactory != null) {
            configureSaxonTransformerFactory(transformerFactory);
        }
        this.transformerFactory = transformerFactory;
    }

    // Helper methods
    //-------------------------------------------------------------------------

    protected void setupFeatures(DocumentBuilderFactory factory) {
        // must do defensive copy in case of concurrency
        Properties properties = new Properties();
        properties.putAll(System.getProperties());

        List<String> features = new ArrayList<>();
        for (Map.Entry<Object, Object> prop : properties.entrySet()) {
            String key = (String) prop.getKey();
            if (key.startsWith(XmlConverter.DOCUMENT_BUILDER_FACTORY_FEATURE)) {
                String uri = StringHelper.after(key, ":");
                boolean value = Boolean.parseBoolean((String) prop.getValue());
                try {
                    factory.setFeature(uri, value);
                    features.add("feature " + uri + " value " + value);
                } catch (ParserConfigurationException e) {
                    LOG.warn("DocumentBuilderFactory doesn't support the feature {} with value {}, due to {}.", uri,
                            value, e.getMessage(), e);
                }
            }
        }
        if (!features.isEmpty()) {
            StringBuilder featureString = new StringBuilder();
            // just log the configured feature
            for (String feature : features) {
                if (!featureString.isEmpty()) {
                    featureString.append(", ");
                }
                featureString.append(feature);
            }
            LOG.info("DocumentBuilderFactory has been set with features {{}}.", featureString);
        }

    }

    public DocumentBuilderFactory getDocumentBuilderFactory(Exchange exchange) {
        DocumentBuilderFactory answer = getDocumentBuilderFactory();
        // Get the DocumentBuilderFactory from the exchange header first
        if (exchange != null) {
            DocumentBuilderFactory factory
                    = exchange.getProperty(Exchange.DOCUMENT_BUILDER_FACTORY, DocumentBuilderFactory.class);
            if (factory != null) {
                answer = factory;
            }
        }
        return answer;
    }

    public DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        try {
            // Set secure processing
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        } catch (ParserConfigurationException e) {
            LOG.warn("DocumentBuilderFactory doesn't support the feature {} with value {}, due to {}.",
                    XMLConstants.FEATURE_SECURE_PROCESSING, true, e.getMessage(), e);
        }
        try {
            // disable DOCTYPE declaration
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", Boolean.TRUE);
        } catch (ParserConfigurationException e) {
            LOG.warn("DocumentBuilderFactory doesn't support the feature {} with value {}, due to {}.",
                    "http://apache.org/xml/features/disallow-doctype-decl", true, e.getMessage(), e);
        }
        try {
            // Disable the external-general-entities by default
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException e) {
            LOG.warn("DocumentBuilderFactory doesn't support the feature {} with value {}, due to {}.",
                    "http://xml.org/sax/features/external-general-entities", false, e.getMessage(), e);
        }
        // setup the SecurityManager by default if it's apache xerces
        try {
            Class<?> smClass = ObjectHelper.loadClass("org.apache.xerces.util.SecurityManager");
            if (smClass != null) {
                Object sm = smClass.getDeclaredConstructor().newInstance();
                // Here we just use the default setting of the SeurityManager
                factory.setAttribute("http://apache.org/xml/properties/security-manager", sm);
            }
        } catch (Exception e) {
            LOG.warn("DocumentBuilderFactory doesn't support the attribute {}, due to {}.",
                    "http://apache.org/xml/properties/security-manager", e.getMessage(), e);
        }
        // setup the feature from the system property
        setupFeatures(factory);
        return factory;
    }

    public DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
        return createDocumentBuilder(getDocumentBuilderFactory());
    }

    public DocumentBuilder createDocumentBuilder(DocumentBuilderFactory factory) throws ParserConfigurationException {
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(DOCUMENT_BUILDER_LOGGING_ERROR_HANDLER);
        return builder;
    }

    public Document createDocument() throws ParserConfigurationException {
        DocumentBuilder builder = createDocumentBuilder();
        return builder.newDocument();
    }

    public Transformer createTransformer() throws TransformerConfigurationException {
        TransformerFactory factory = getTransformerFactory();
        return factory.newTransformer();
    }

    public TransformerFactory createTransformerFactory() {
        TransformerFactory factory;
        TransformerFactoryConfigurationError cause;
        try {
            factory = TransformerFactory.newInstance();
        } catch (TransformerFactoryConfigurationError e) {
            cause = e;
            // try fallback from the JDK
            try {
                LOG.debug(
                        "Cannot create/load TransformerFactory due: {}. Will attempt to use JDK fallback TransformerFactory: {}",
                        e.getMessage(), JDK_FALLBACK_TRANSFORMER_FACTORY);
                factory = TransformerFactory.newInstance(JDK_FALLBACK_TRANSFORMER_FACTORY, null);
            } catch (Exception t) {
                // okay we cannot load fallback then throw original exception
                throw cause;
            }
        }
        LOG.debug("Created TransformerFactory: {}", factory);

        // Enable the Security feature by default
        try {
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException e) {
            LOG.warn("TransformerFactory doesn't support the feature {} with value {}, due to {}.",
                    javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, "true", e.getMessage());
        }
        LOG.debug("Configuring TransformerFactory to not allow access to external DTD/Stylesheet");
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (Exception e) {
            // ignore
        }
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (Exception e) {
            // ignore
        }
        factory.setErrorListener(new XmlErrorListener());
        configureSaxonTransformerFactory(factory);
        return factory;
    }

    /**
     * Make a Saxon TransformerFactory more JAXP compliant by configuring it to send &lt;xsl:message&gt; output to the
     * ErrorListener.
     *
     * @param factory the TransformerFactory
     */
    public void configureSaxonTransformerFactory(TransformerFactory factory) {
        // check whether we have a Saxon TransformerFactory ("net.sf.saxon" for open source editions (HE / B)
        // and "com.saxonica" for commercial editions (PE / EE / SA))
        Class<?> factoryClass = factory.getClass();
        if (factoryClass.getName().startsWith("net.sf.saxon")
                || factoryClass.getName().startsWith("com.saxonica")) {

            // just in case there are multiple class loaders with different Saxon versions, use the
            // TransformerFactory's class loader to find Saxon support classes
            ClassLoader loader = factoryClass.getClassLoader();

            int[] version = retrieveSaxonVersion(loader);

            if (null != version && version[0] < 12) {
                // try to find Saxon's MessageWarner class that redirects <xsl:message> to the ErrorListener
                Class<?> messageWarner = null;
                // Saxon [9.3, 11]
                if (version[0] > 9 || version[0] == 9 && version[1] >= 3) {
                    try {
                        messageWarner = loader.loadClass("net.sf.saxon.serialize.MessageWarner");
                    } catch (ClassNotFoundException e) {
                        LOG.warn("Error loading Saxon's net.sf.saxon.serialize.MessageWarner class from the classpath!"
                                 + " <xsl:message> output will not be redirected to the ErrorListener!");
                    }
                } else {
                    try {
                        // Saxon < 9.3 (including Saxon-B / -SA)
                        messageWarner = loader.loadClass("net.sf.saxon.event.MessageWarner");
                    } catch (ClassNotFoundException cnfe2) {
                        LOG.warn("Error loading Saxon's net.sf.saxon.event.MessageWarner class from the classpath!"
                                 + " <xsl:message> output will not be redirected to the ErrorListener!");
                    }
                }

                if (messageWarner != null) {
                    // set net.sf.saxon.FeatureKeys.MESSAGE_EMITTER_CLASS
                    factory.setAttribute("http://saxon.sf.net/feature/messageEmitterClass", messageWarner.getName());
                }
            }
        }
    }

    private int[] retrieveSaxonVersion(ClassLoader loader) {
        try {
            final Class<?> versionClass = loader.loadClass("net.sf.saxon.Version");
            final Method method = versionClass.getDeclaredMethod("getStructuredVersionNumber");
            final Object result = method.invoke(null);
            return (int[]) result;
        } catch (ClassNotFoundException e) {
            LOG.warn("Error loading Saxon's net.sf.saxon.Version class from the classpath!");
        } catch (InvocationTargetException e) {
            LOG.warn("Error retrieving Saxon version from net.sf.saxon.Version!");
        } catch (NoSuchMethodException e) {
            LOG.warn("Method getStructuredVersionNumber not available on net.sf.saxon.Version!");
        } catch (IllegalAccessException e) {
            LOG.warn("Unable to access method getStructuredVersionNumber on net.sf.saxon.Version!");
        }

        return null;
    }

    public SAXParserFactory createSAXParserFactory() {
        SAXParserFactory sfactory = SAXParserFactory.newInstance();
        // Need to setup XMLReader security feature by default
        try {
            sfactory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Exception e) {
            LOG.warn("SAXParser doesn't support the feature {} with value {}, due to {}.",
                    javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, "true", e.getMessage());
        }
        try {
            sfactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception e) {
            LOG.warn("SAXParser doesn't support the feature {} with value {}, due to {}.",
                    "http://xml.org/sax/features/external-general-entities", false, e.getMessage());
        }
        sfactory.setNamespaceAware(true);
        return sfactory;
    }

    private static class DocumentBuilderLoggingErrorHandler implements ErrorHandler {

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            LOG.warn(exception.getMessage(), exception);
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            LOG.error(exception.getMessage(), exception);
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            LOG.error(exception.getMessage(), exception);
        }
    }
}
