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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A converter of StAX objects
 */
@Converter(generateLoader = true)
public class StaxConverter {
    private static final Logger LOG = LoggerFactory.getLogger(StaxConverter.class);

    private static final BlockingQueue<XMLInputFactory> INPUT_FACTORY_POOL;
    private static final BlockingQueue<XMLOutputFactory> OUTPUT_FACTORY_POOL;
    static {
        int i = 20;
        try {
            String s = AccessController.doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return System.getProperty("org.apache.cxf.staxutils.pool-size", "-1");
                }
            });
            i = Integer.parseInt(s);
        } catch (Throwable t) {
            //ignore 
            i = 20;
        }
        try {
            // if we have more cores than 20, then use that
            int cores = Runtime.getRuntime().availableProcessors();
            if (cores > i) {
                i = cores;
            }
        } catch (Throwable t) {
            // ignore
            i = 20;
        }

        if (i <= 0) {
            i = 20;
        }

        LOG.debug("StaxConverter pool size: {}", i);

        INPUT_FACTORY_POOL = new LinkedBlockingQueue<>(i);
        OUTPUT_FACTORY_POOL = new LinkedBlockingQueue<>(i);
    }
    
    private XMLInputFactory inputFactory;
    private XMLOutputFactory outputFactory;

    @Converter
    public XMLEventWriter createXMLEventWriter(OutputStream out, Exchange exchange) throws XMLStreamException {
        XMLOutputFactory factory = getOutputFactory();
        try {
            return factory.createXMLEventWriter(IOHelper.buffered(out), ExchangeHelper.getCharsetName(exchange));
        } finally {
            returnXMLOutputFactory(factory);
        }
    }
    
    @Converter
    public XMLEventWriter createXMLEventWriter(Writer writer) throws XMLStreamException {
        XMLOutputFactory factory = getOutputFactory();
        try {
            return factory.createXMLEventWriter(IOHelper.buffered(writer));
        } finally {
            returnXMLOutputFactory(factory);
        }
    }

    @Converter
    public XMLEventWriter createXMLEventWriter(Result result) throws XMLStreamException {
        XMLOutputFactory factory = getOutputFactory();
        try {
            return factory.createXMLEventWriter(result);
        } finally {
            returnXMLOutputFactory(factory);
        }
    }
    
    @Converter
    public XMLStreamWriter createXMLStreamWriter(OutputStream outputStream, Exchange exchange) throws XMLStreamException {
        XMLOutputFactory factory = getOutputFactory();
        try {
            return factory.createXMLStreamWriter(IOHelper.buffered(outputStream), ExchangeHelper.getCharsetName(exchange));
        } finally {
            returnXMLOutputFactory(factory);
        }
    }

    @Converter
    public XMLStreamWriter createXMLStreamWriter(Writer writer) throws XMLStreamException {
        XMLOutputFactory factory = getOutputFactory();
        try {
            return factory.createXMLStreamWriter(IOHelper.buffered(writer));
        } finally {
            returnXMLOutputFactory(factory);
        }
    }

    @Converter
    public XMLStreamWriter createXMLStreamWriter(Result result) throws XMLStreamException {
        XMLOutputFactory factory = getOutputFactory();
        try {
            return factory.createXMLStreamWriter(result);
        } finally {
            returnXMLOutputFactory(factory);
        }
    }

    @Converter
    public XMLStreamReader createXMLStreamReader(InputStream in, Exchange exchange) throws XMLStreamException {
        XMLInputFactory factory = getInputFactory();
        try {
            String charsetName = ExchangeHelper.getCharsetName(exchange, false);
            if (charsetName == null) {
                return factory.createXMLStreamReader(IOHelper.buffered(in));
            } else {
                return factory.createXMLStreamReader(IOHelper.buffered(in), charsetName);
            }
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLStreamReader createXMLStreamReader(File file, Exchange exchange) throws XMLStreamException, FileNotFoundException {
        XMLInputFactory factory = getInputFactory();
        try {
            return factory.createXMLStreamReader(IOHelper.buffered(new FileInputStream(file)), ExchangeHelper.getCharsetName(exchange));
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLStreamReader createXMLStreamReader(Reader reader) throws XMLStreamException {
        XMLInputFactory factory = getInputFactory();
        try {
            return factory.createXMLStreamReader(IOHelper.buffered(reader));
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLStreamReader createXMLStreamReader(Source in) throws XMLStreamException {
        XMLInputFactory factory = getInputFactory();
        try {
            return factory.createXMLStreamReader(in);
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLStreamReader createXMLStreamReader(String string) throws XMLStreamException {
        XMLInputFactory factory = getInputFactory();
        try {
            return factory.createXMLStreamReader(new StringReader(string));
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLEventReader createXMLEventReader(InputStream in, Exchange exchange) throws XMLStreamException {
        XMLInputFactory factory = getInputFactory();
        try {
            String charsetName = ExchangeHelper.getCharsetName(exchange, false);
            if (charsetName == null) {
                return factory.createXMLEventReader(IOHelper.buffered(in));
            } else {
                return factory.createXMLEventReader(IOHelper.buffered(in), charsetName);
            }
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLEventReader createXMLEventReader(File file, Exchange exchange) throws XMLStreamException, FileNotFoundException {
        XMLInputFactory factory = getInputFactory();
        try {
            return factory.createXMLEventReader(IOHelper.buffered(new FileInputStream(file)), ExchangeHelper.getCharsetName(exchange));
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLEventReader createXMLEventReader(Reader reader) throws XMLStreamException {
        XMLInputFactory factory = getInputFactory();
        try {
            return factory.createXMLEventReader(IOHelper.buffered(reader));
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLEventReader createXMLEventReader(XMLStreamReader reader) throws XMLStreamException {
        XMLInputFactory factory = getInputFactory();
        try {
            return factory.createXMLEventReader(reader);
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public XMLEventReader createXMLEventReader(Source in) throws XMLStreamException {
        XMLInputFactory factory = getInputFactory();
        try {
            return factory.createXMLEventReader(in);
        } finally {
            returnXMLInputFactory(factory);
        }
    }

    @Converter
    public InputStream createInputStream(XMLStreamReader reader, Exchange exchange) {
        XMLOutputFactory factory = getOutputFactory();
        try {
            String charsetName = ExchangeHelper.getCharsetName(exchange, false);
            return new XMLStreamReaderInputStream(reader, charsetName, factory);
        } finally {
            returnXMLOutputFactory(factory);
        }
    }

    @Converter
    public Reader createReader(XMLStreamReader reader, Exchange exchange) {
        XMLOutputFactory factory = getOutputFactory();
        try {
            return new XMLStreamReaderReader(reader, factory);
        } finally {
            returnXMLOutputFactory(factory);
        }
    }

    private static boolean isWoodstox(Object factory) {
        return factory.getClass().getPackage().getName().startsWith("com.ctc.wstx");
    }

    private XMLInputFactory getXMLInputFactory() {
        XMLInputFactory f = INPUT_FACTORY_POOL.poll();
        if (f == null) {
            f = createXMLInputFactory(true);
        }
        return f;
    }
    
    private void returnXMLInputFactory(XMLInputFactory factory) {
        if (factory != inputFactory) {
            INPUT_FACTORY_POOL.offer(factory);
        }
    }
    
    private XMLOutputFactory getXMLOutputFactory() {
        XMLOutputFactory f = OUTPUT_FACTORY_POOL.poll();
        if (f == null) {
            f = XMLOutputFactory.newInstance();
        }
        return f;
    }
    
    private void returnXMLOutputFactory(XMLOutputFactory factory) {
        if (factory != outputFactory) {
            OUTPUT_FACTORY_POOL.offer(factory);
        }
    }
    
    public static XMLInputFactory createXMLInputFactory(boolean nsAware) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        setProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, nsAware);
        setProperty(factory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        setProperty(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        setProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setXMLResolver(new XMLResolver() {
            public Object resolveEntity(String publicID, String systemID,
                                        String baseURI, String namespace)
                throws XMLStreamException {
                throw new XMLStreamException("Reading external entities is disabled");
            }
        });

        if (isWoodstox(factory)) {
            // just log a debug as we are good then
            LOG.debug("Created Woodstox XMLInputFactory: {}", factory);
        } else {
            // log a hint that woodstock may be a better factory to use
            LOG.info("Created XMLInputFactory: {}. DOMSource/DOMResult may have issues with {}. We suggest using Woodstox.", factory, factory);
        }
        return factory;
    }
    
    private static void setProperty(XMLInputFactory f, String p, Object o) {
        try {
            f.setProperty(p,  o);
        } catch (Throwable t) {
            //ignore
        }
    }
    
    // Properties
    //-------------------------------------------------------------------------

    public XMLInputFactory getInputFactory() {
        if (inputFactory == null) {
            return getXMLInputFactory();
        }
        return inputFactory;
    }

    public XMLOutputFactory getOutputFactory() {
        if (outputFactory == null) {
            return getXMLOutputFactory();
        }
        return outputFactory;
    }
    
    public void setInputFactory(XMLInputFactory inputFactory) {
        this.inputFactory = inputFactory;
    }

    public void setOutputFactory(XMLOutputFactory outputFactory) {
        this.outputFactory = outputFactory;
    }

}
