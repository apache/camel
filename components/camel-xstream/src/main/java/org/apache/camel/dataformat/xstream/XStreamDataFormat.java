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
package org.apache.camel.dataformat.xstream;

import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.QNameMap;
import com.thoughtworks.xstream.io.xml.StaxReader;
import com.thoughtworks.xstream.io.xml.StaxWriter;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.util.IOHelper;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a>
 * ({@link DataFormat}) using XStream to marshal to and from XML
 */
@Dataformat("xstream")
@Metadata(includeProperties = "encoding,converters,aliases,omitFields,implicitCollections,permissions,mode,contentTypeHeader")
public class XStreamDataFormat extends AbstractXStreamWrapper  {
    private String encoding;
    
    public XStreamDataFormat() {
    }

    public XStreamDataFormat(XStream xstream) {
        super(xstream);
    }

    @Override
    public String getDataFormatName() {
        return "xstream";
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
    
    public String getEncoding() {
        return encoding;
    }

    @Override
    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        super.marshal(exchange, body, stream);

        if (isContentTypeHeader()) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            } else {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            }
        }
    }

    /**
     * A factory method which takes a collection of types to be annotated
     */
    @Deprecated
    public static XStreamDataFormat processAnnotations(ClassResolver resolver, Iterable<Class<?>> types) {
        XStreamDataFormat answer = new XStreamDataFormat();
        XStream xstream = answer.getXStream(resolver);
        for (Class<?> type : types) {
            xstream.processAnnotations(type);
        }
        return answer;
    }

    /**
     * A factory method which takes a number of types to be annotated
     */
    @Deprecated
    public static XStreamDataFormat processAnnotations(ClassResolver resolver, Class<?>... types) {
        XStreamDataFormat answer = new XStreamDataFormat();
        XStream xstream = answer.getXStream(resolver);
        for (Class<?> type : types) {
            xstream.processAnnotations(type);
        }
        return answer;
    }
    
    // just make sure the exchange property can override the xmlstream encoding setting
    protected void updateCharacterEncodingInfo(Exchange exchange) {
        if (exchange.getProperty(Exchange.CHARSET_NAME) == null && encoding != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.normalizeCharset(encoding));
        }
    }

    @Override
    protected HierarchicalStreamWriter createHierarchicalStreamWriter(Exchange exchange, Object body, OutputStream stream) throws XMLStreamException {
        updateCharacterEncodingInfo(exchange);
        if (getXstreamDriver() != null) {
            return getXstreamDriver().createWriter(stream);
        }
        XMLStreamWriter xmlWriter = exchange.getContext().getTypeConverter().convertTo(XMLStreamWriter.class, exchange, stream);
        return new StaxWriter(new QNameMap(), xmlWriter);
    }

    @Override
    protected HierarchicalStreamReader createHierarchicalStreamReader(Exchange exchange, InputStream stream) throws XMLStreamException {
        updateCharacterEncodingInfo(exchange);
        if (getXstreamDriver() != null) {
            return getXstreamDriver().createReader(stream);
        }
        XMLStreamReader xmlReader = exchange.getContext().getTypeConverter().convertTo(XMLStreamReader.class, exchange, stream);
        return new StaxReader(new QNameMap(), xmlReader);
    }
}
