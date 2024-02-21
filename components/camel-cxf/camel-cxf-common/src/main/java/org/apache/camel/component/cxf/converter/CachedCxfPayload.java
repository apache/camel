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
package org.apache.camel.component.cxf.converter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.support.builder.xml.XMLConverterHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.xml.StreamSourceCache;
import org.apache.cxf.staxutils.StaxSource;
import org.apache.cxf.staxutils.StaxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link org.apache.camel.StreamCache} implementation for CXF payload.
 * <p/>
 * <b>Important:</b> All the classes from the Camel release that implements {@link StreamCache} is NOT intended for end
 * users to create as instances, but they are part of Camels
 * <a href="https://camel.apache.org/manual/stream-caching.html">stream-caching</a> functionality.
 */
public class CachedCxfPayload<T> extends CxfPayload<T> implements StreamCache {
    private static final Logger LOG = LoggerFactory.getLogger(CachedCxfPayload.class);
    private static String defaultCharset = ObjectHelper.getSystemProperty("org.apache.camel.default.charset", "UTF-8");

    public CachedCxfPayload(CxfPayload<T> orig, Exchange exchange) {
        super(orig.getHeaders(), new ArrayList<>(orig.getBodySources()), orig.getNsMap());
        ListIterator<Source> li = getBodySources().listIterator();
        while (li.hasNext()) {
            Source source = li.next();
            XMLStreamReader reader = null;
            // namespace definitions that are on the SOAP envelope can get lost, if this is
            // not a DOM (there is special coding on the CXFPayload.getBody().get() method for
            // this, that only works on DOM nodes.
            // We have to do some delegation on the XMLStreamReader for StAXSource and StaxSource
            // that re-injects the missing namespaces into the XMLStreamReader.
            // Replace all other Sources that are not DOMSources with DOMSources.
            if (source instanceof StaxSource) {
                reader = ((StaxSource) source).getXMLStreamReader();
            } else if (source instanceof StAXSource) {
                reader = ((StAXSource) source).getXMLStreamReader();
            }
            if (reader != null) {
                Map<String, String> nsmap = getNsMap();
                if (nsmap != null && !(reader instanceof DelegatingXMLStreamReader)) {
                    reader = new DelegatingXMLStreamReader(reader, nsmap);
                }
                CachedOutputStream cos = new CachedOutputStream(exchange);
                try {
                    StaxUtils.copy(reader, cos);
                    li.set(new StreamSourceCache(cos.newStreamCache()));
                    // this worked so continue
                    continue;
                } catch (Exception e) {
                    // fallback to trying to read the reader using another way
                    StreamResult sr = new StreamResult(cos);
                    try {
                        toResult(source, sr);
                        li.set(new StreamSourceCache(cos.newStreamCache()));
                        // this worked so continue
                        continue;
                    } catch (Exception e2) {
                        // ignore did not work so we will fallback to DOM mode
                        // this can happens in some rare cases such as reported by CAMEL-11681
                        LOG.debug(
                                "Error during parsing XMLStreamReader from StaxSource/StAXSource. Will fallback to using DOM mode. This exception is ignored",
                                e2);
                    }
                }
            }
            // fallback to using DOM
            DOMSource document = exchange.getContext().getTypeConverter().tryConvertTo(DOMSource.class, exchange, source);
            if (document != null) {
                li.set(document);
            }
        }
        orig.setBodySources(getBodySources());
    }

    private CachedCxfPayload(CachedCxfPayload<T> orig, Exchange exchange) throws IOException {
        super(orig.getHeaders(), new ArrayList<>(orig.getBodySources()), orig.getNsMap());
        ListIterator<Source> li = getBodySources().listIterator();
        while (li.hasNext()) {
            Source source = li.next();
            if (source instanceof StreamCache) {
                li.set((Source) (((StreamCache) source)).copy(exchange));
            }
        }
    }

    private static void toResult(Source source, Result result) throws TransformerException {
        if (source != null) {
            XMLConverterHelper xml = new XMLConverterHelper();
            TransformerFactory factory = xml.getTransformerFactory();
            Transformer transformer = factory.newTransformer();
            if (transformer == null) {
                throw new TransformerException("Could not create a transformer - JAXP is misconfigured!");
            } else {
                Properties outputProperties = new Properties();
                outputProperties.put("encoding", defaultCharset);
                outputProperties.put("omit-xml-declaration", "yes");

                transformer.setOutputProperties(outputProperties);
                transformer.transform(source, result);
            }
        }
    }

    @Override
    public void reset() {
        for (Source source : getBodySources()) {
            if (source instanceof StreamCache) {
                ((StreamCache) source).reset();
            }
        }
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        // no body no write
        if (getBodySources().isEmpty()) {
            return;
        }
        Source body = getBodySources().get(0);
        if (body instanceof StreamCache) {
            ((StreamCache) body).writeTo(os);
        } else {
            try {
                StaxUtils.copy(body, os);
            } catch (XMLStreamException e) {
                throw new IOException("Transformation failed", e);
            }
        }
    }

    @Override
    public boolean inMemory() {
        boolean inMemory = true;
        for (Source source : getBodySources()) {
            if (source instanceof StreamCache && !((StreamCache) source).inMemory()) {
                inMemory = false;
            }
        }
        return inMemory;
    }

    @Override
    public long length() {
        return 0;
    }

    @Override
    public long position() {
        return -1;
    }

    @Override
    public StreamCache copy(Exchange exchange) throws IOException {
        return new CachedCxfPayload<>(this, exchange);
    }
}
