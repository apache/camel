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
import org.apache.camel.converter.jaxp.StaxConverter;
import org.apache.camel.spi.DataFormat;

/**
 * A <a href="http://activemq.apache.org/camel/data-format.html">data format</a>
 * ({@link DataFormat}) using XStream to marshal to and from XML
 *
 * @version $Revision$
 */
public class XStreamDataFormat extends AbstractXStreamWrapper  {
    
    public XStreamDataFormat() {
        super();
    }

    public XStreamDataFormat(XStream xstream) {
        super(xstream);
    }

    /**
     * A factory method which takes a collection of types to be annotated
     */
    public static XStreamDataFormat processAnnotations(Iterable<Class<?>> types) {
        XStreamDataFormat answer = new XStreamDataFormat();
        XStream xstream = answer.getXStream();
        for (Class<?> type : types) {
            xstream.processAnnotations(type);
        }
        return answer;
    }

    /**
     * A factory method which takes a number of types to be annotated
     */
    public static XStreamDataFormat processAnnotations(Class<?>... types) {
        XStreamDataFormat answer = new XStreamDataFormat();
        XStream xstream = answer.getXStream();
        for (Class<?> type : types) {
            xstream.processAnnotations(type);
        }
        return answer;
    }    

    protected HierarchicalStreamWriter createHierarchicalStreamWriter(Exchange exchange, Object body, OutputStream stream) throws XMLStreamException {
        XMLStreamWriter xmlWriter = getStaxConverter().createXMLStreamWriter(stream);
        return new StaxWriter(new QNameMap(), xmlWriter);
    }

    protected HierarchicalStreamReader createHierarchicalStreamReader(Exchange exchange, InputStream stream) throws XMLStreamException {
        XMLStreamReader xmlReader = getStaxConverter().createXMLStreamReader(stream);
        return new StaxReader(new QNameMap(), xmlReader);
    }
}
