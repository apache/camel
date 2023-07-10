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
package org.apache.camel.component.xslt.saxon;

import java.io.Writer;
import java.util.Properties;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXSource;

import net.sf.saxon.jaxp.TemplatesImpl;
import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.lib.StandardMessageHandler;
import net.sf.saxon.str.UnicodeWriter;
import net.sf.saxon.str.UnicodeWriterToWriter;
import org.apache.camel.component.xslt.XmlSourceHandlerFactoryImpl;
import org.apache.camel.component.xslt.XsltBuilder;
import org.apache.camel.component.xslt.XsltMessageLogger;
import org.apache.camel.support.builder.xml.StAX2SAXSource;

public class XsltSaxonBuilder extends XsltBuilder {

    private boolean allowStAX = true;

    @Override
    protected Source prepareSource(Source source) {
        if (!isAllowStAX() && source instanceof StAXSource) {
            // Always convert StAXSource to SAXSource.
            // * Xalan and Saxon-B don't support StAXSource.
            // * The JDK default implementation (XSLTC) doesn't handle CDATA events
            //   (see com.sun.org.apache.xalan.internal.xsltc.trax.StAXStream2SAX).
            // * Saxon-HE/PE/EE seem to support StAXSource, but don't advertise this
            //   officially (via TransformerFactory.getFeature(StAXSource.FEATURE))
            source = new StAX2SAXSource(((StAXSource) source).getXMLStreamReader());
        }
        return source;
    }

    // Properties
    // -------------------------------------------------------------------------

    public boolean isAllowStAX() {
        return allowStAX;
    }

    public void setAllowStAX(boolean allowStAX) {
        this.allowStAX = allowStAX;
    }

    @Override
    protected XmlSourceHandlerFactoryImpl createXmlSourceHandlerFactoryImpl() {
        return new SaxonXmlSourceHandlerFactoryImpl();
    }

    @Override
    protected Templates createTemplates(TransformerFactory factory, Source source) throws TransformerConfigurationException {
        final Templates templates = super.createTemplates(factory, source);
        if (templates instanceof TemplatesImpl && getXsltMessageLogger() != null) {
            return new MessageDelegatingTemplates((TemplatesImpl) templates, getXsltMessageLogger());
        }
        return templates;
    }

    private static class MessageConsumerWriter extends Writer {

        private final XsltMessageLogger xsltMessageLogger;

        public MessageConsumerWriter(XsltMessageLogger xsltMessageLogger) {
            this.xsltMessageLogger = xsltMessageLogger;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            if (len > 0) {
                xsltMessageLogger.accept(String.copyValueOf(cbuf, off, len));
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            flush();
        }
    }

    static class MessageDelegatingTemplates implements Templates {

        private final TemplatesImpl delegated;

        private final XsltMessageLogger xsltMessageLogger;

        MessageDelegatingTemplates(TemplatesImpl templates, XsltMessageLogger xsltMessageLogger) {
            this.delegated = templates;
            this.xsltMessageLogger = xsltMessageLogger;
        }

        @Override
        public Transformer newTransformer() throws TransformerConfigurationException {
            final TransformerImpl transformer = (TransformerImpl) delegated.newTransformer();
            final StandardMessageHandler standardMessageHandler = new StandardMessageHandler(transformer.getConfiguration());
            final UnicodeWriter writer = new UnicodeWriterToWriter(new MessageConsumerWriter(xsltMessageLogger));
            standardMessageHandler.setUnicodeWriter(writer);
            transformer.getUnderlyingXsltTransformer().setMessageHandler(standardMessageHandler);

            return transformer;
        }

        @Override
        public Properties getOutputProperties() {
            return delegated.getOutputProperties();
        }
    }
}
