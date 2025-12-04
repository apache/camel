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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;

import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.camel.Exchange;
import org.apache.camel.component.xslt.XmlSourceHandlerFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaxonXmlSourceHandlerFactoryImpl extends XmlSourceHandlerFactoryImpl {

    private static final Logger LOG = LoggerFactory.getLogger(SaxonXmlSourceHandlerFactoryImpl.class);

    private boolean useJsonBody = false;

    public boolean isUseJsonBody() {
        return useJsonBody;
    }

    public void setUseJsonBody(boolean useJsonBody) {
        this.useJsonBody = useJsonBody;
    }

    private net.sf.saxon.s9api.Processor saxonProcessor;
    private net.sf.saxon.s9api.XPathExecutable saxonJsonToXmlExecutable;
    private DocumentBuilder documentBuilder;

    @Override
    protected Source getSource(Exchange exchange, Object body) {
        // body may already be a source
        if (body instanceof Source) {
            return (Source) body;
        }

        if (useJsonBody && body != null) {
            try {
                String jsonString = exchange.getContext().getTypeConverter().convertTo(String.class, body);
                if (jsonString != null) {
                    Source xmlSource = convertJsonToXmlSource(jsonString);
                    if (xmlSource != null) {
                        LOG.debug("Converted JSON input to XML using XSLT3 json-to-xml() function");
                        return xmlSource;
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to convert JSON to XML, falling back to standard processing: {}", e.getMessage());
            }
        }

        Source source = null;
        if (body != null) {
            // try StAX if enabled
            source = exchange.getContext().getTypeConverter().tryConvertTo(StAXSource.class, exchange, body);
        }
        if (source == null) {
            source = super.getSource(exchange, body);
        }
        return source;
    }

    private Source convertJsonToXmlSource(String jsonString) {
        try {
            net.sf.saxon.s9api.XPathSelector selector =
                    getSaxonJsonToXmlExecutable().load();
            selector.setContextItem(new net.sf.saxon.s9api.XdmAtomicValue(jsonString));
            net.sf.saxon.s9api.XdmValue result = selector.evaluate();

            if (!result.isEmpty()) {
                net.sf.saxon.s9api.XdmItem item = result.itemAt(0);
                if (item instanceof net.sf.saxon.s9api.XdmNode xdmNode) {
                    // The most efficient way would be:
                    //     return xdmNode.getUnderlyingNode();
                    // In order to make it work however, this pre-process and the main XSLT processing later on
                    // have to use same Saxon internal Processor/Configuration. While it would be possible, it
                    // would require a refactoring across camel-xslt and camel-xslt-saxon.
                    var doc = getDocumentBuilder().newDocument();
                    DOMDestination domDest = new DOMDestination(doc);
                    saxonProcessor.writeXdmValue(xdmNode, domDest);

                    return new DOMSource(doc);
                }
            }

        } catch (SaxonApiException | ParserConfigurationException e) {
            LOG.warn("Failed to convert JSON to XML using XSLT3 json-to-xml() function: {}", e.getMessage());
        }

        return null;
    }

    private synchronized net.sf.saxon.s9api.XPathExecutable getSaxonJsonToXmlExecutable() throws SaxonApiException {
        if (saxonJsonToXmlExecutable == null) {
            saxonProcessor = new Processor(false);
            net.sf.saxon.s9api.XPathCompiler xpathCompiler = saxonProcessor.newXPathCompiler();
            saxonJsonToXmlExecutable = xpathCompiler.compile("json-to-xml(.)");
            LOG.debug("Initialized reusable XPathExecutable for json-to-xml() function");
        }
        return this.saxonJsonToXmlExecutable;
    }

    private synchronized DocumentBuilder getDocumentBuilder() throws SaxonApiException, ParserConfigurationException {
        if (documentBuilder == null) {
            var factory = DocumentBuilderFactory.newInstance();
            documentBuilder = factory.newDocumentBuilder();
        }
        return documentBuilder;
    }
}
