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
package org.apache.camel.converter.jaxp;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Manages a pool of XMLReader (and associated SAXParser) instances for reuse.
 */
public class XMLReaderPool {
    private final Queue<WeakReference<XMLReader>> pool = new ConcurrentLinkedQueue<WeakReference<XMLReader>>();
    private final SAXParserFactory saxParserFactory;

    /**
     * Creates a new instance.
     *
     * @param saxParserFactory
     *            the SAXParserFactory used to create new SAXParser instances
     */
    public XMLReaderPool(SAXParserFactory saxParserFactory) {
        this.saxParserFactory = saxParserFactory;
    }

    /**
     * Returns an XMLReader that can be used exactly once. Calling one of the
     * {@code parse} methods returns the reader to the pool. This is useful
     * for e.g. SAXSource which bundles an XMLReader with an InputSource that
     * can also be consumed just once.
     *
     * @return the XMLReader
     * @throws SAXException
     *             see {@link SAXParserFactory#newSAXParser()}
     * @throws ParserConfigurationException
     *             see {@link SAXParserFactory#newSAXParser()}
     */
    public XMLReader createXMLReader() throws SAXException, ParserConfigurationException {
        XMLReader xmlReader = null;
        WeakReference<XMLReader> ref;
        while ((ref = pool.poll()) != null) {
            if ((xmlReader = ref.get()) != null) {
                break;
            }
        }

        if (xmlReader == null) {
            xmlReader = saxParserFactory.newSAXParser().getXMLReader();
        }

        return new OneTimeXMLReader(xmlReader);
    }

    /**
     * Wraps another XMLReader for single use only.
     */
    private final class OneTimeXMLReader implements XMLReader {
        private final XMLReader xmlReader;
        private final Map<String, Boolean> initFeatures = new HashMap<String, Boolean>();
        private final Map<String, Object> initProperties = new HashMap<String, Object>();
        private final ContentHandler initContentHandler;
        private final DTDHandler initDtdHandler;
        private final EntityResolver initEntityResolver;
        private final ErrorHandler initErrorHandler;
        private boolean readerInvalid;

        private OneTimeXMLReader(XMLReader xmlReader) {
            this.xmlReader = xmlReader;
            this.initContentHandler = xmlReader.getContentHandler();
            this.initDtdHandler = xmlReader.getDTDHandler();
            this.initEntityResolver = xmlReader.getEntityResolver();
            this.initErrorHandler = xmlReader.getErrorHandler();
        }

        private void release() {
            try {
                // reset XMLReader to its initial state
                for (Map.Entry<String, Boolean> feature : initFeatures.entrySet()) {
                    try {
                        xmlReader.setFeature(feature.getKey(), feature.getValue().booleanValue());
                    } catch (Exception e) {
                        // ignore
                    }
                }
                for (Map.Entry<String, Object> property : initProperties.entrySet()) {
                    try {
                        xmlReader.setProperty(property.getKey(), property.getValue());
                    } catch (Exception e) {
                        // ignore
                    }
                }
                xmlReader.setContentHandler(initContentHandler);
                xmlReader.setDTDHandler(initDtdHandler);
                xmlReader.setEntityResolver(initEntityResolver);
                xmlReader.setErrorHandler(initErrorHandler);

                // return the wrapped instance to the pool
                pool.offer(new WeakReference<XMLReader>(xmlReader));
            } finally {
                readerInvalid = true;
            }
        }

        @Override
        public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return xmlReader.getFeature(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            if (!readerInvalid) {
                if (!initFeatures.containsKey(name)) {
                    initFeatures.put(name, Boolean.valueOf(xmlReader.getFeature(name)));
                }
                xmlReader.setFeature(name, value);
            }
        }

        @Override
        public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return xmlReader.getProperty(name);
        }

        @Override
        public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
            if (!readerInvalid) {
                if (!initProperties.containsKey(name)) {
                    initProperties.put(name, xmlReader.getProperty(name));
                }
                xmlReader.setProperty(name, value);
            }
        }

        @Override
        public ContentHandler getContentHandler() {
            return xmlReader.getContentHandler();
        }

        @Override
        public void setContentHandler(ContentHandler handler) {
            if (!readerInvalid) {
                xmlReader.setContentHandler(handler);
            }
        }

        @Override
        public DTDHandler getDTDHandler() {
            return xmlReader.getDTDHandler();
        }

        @Override
        public void setDTDHandler(DTDHandler handler) {
            if (!readerInvalid) {
                xmlReader.setDTDHandler(handler);
            }
        }

        @Override
        public EntityResolver getEntityResolver() {
            return xmlReader.getEntityResolver();
        }

        @Override
        public void setEntityResolver(EntityResolver resolver) {
            if (!readerInvalid) {
                xmlReader.setEntityResolver(resolver);
            }
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return xmlReader.getErrorHandler();
        }

        @Override
        public void setErrorHandler(ErrorHandler handler) {
            if (!readerInvalid) {
                xmlReader.setErrorHandler(handler);
            }
        }

        @Override
        public synchronized void parse(InputSource input) throws IOException, SAXException {
            checkValid();
            try {
                xmlReader.parse(input);
            } finally {
                release();
            }
        }

        @Override
        public synchronized void parse(String systemId) throws IOException, SAXException {
            checkValid();
            try {
                xmlReader.parse(systemId);
            } finally {
                release();
            }
        }
        
        private void checkValid() {
            if (readerInvalid) {
                throw new IllegalStateException("OneTimeXMLReader can only be used once!");
            }
        }
    }
}
