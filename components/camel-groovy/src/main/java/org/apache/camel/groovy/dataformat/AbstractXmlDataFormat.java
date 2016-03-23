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
package org.apache.camel.groovy.dataformat;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import groovy.xml.FactorySupport;

import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common attributes and methods for XmlParser and XmlSlurper usage.
 */
public abstract class AbstractXmlDataFormat extends ServiceSupport implements DataFormat {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractXmlDataFormat.class);
    private static final ErrorHandler DEFAULT_HANDLER = new DefaultErrorHandler();

    private boolean namespaceAware = true;
    private boolean keepWhitespace;
    private ErrorHandler errorHandler = DEFAULT_HANDLER;

    public AbstractXmlDataFormat(boolean namespaceAware) {
        this.namespaceAware = namespaceAware;
    }

    protected SAXParser newSaxParser() throws Exception {
        SAXParserFactory factory = FactorySupport.createSaxParserFactory();
        factory.setNamespaceAware(namespaceAware);
        factory.setValidating(false);
        return factory.newSAXParser();
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public boolean isNamespaceAware() {
        return namespaceAware;
    }

    public void setNamespaceAware(boolean namespaceAware) {
        this.namespaceAware = namespaceAware;
    }

    public boolean isKeepWhitespace() {
        return keepWhitespace;
    }

    public void setKeepWhitespace(boolean keepWhitespace) {
        this.keepWhitespace = keepWhitespace;
    }

    private static class DefaultErrorHandler implements ErrorHandler {

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            LOG.warn("Warning occured during parsing", exception);
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw new SAXException(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw new SAXException(exception);
        }

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
