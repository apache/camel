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
package org.apache.camel.converter.xmlbeans;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

import javax.xml.transform.Source;

import org.w3c.dom.Node;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.NIOConverter;
import org.apache.camel.util.ObjectHelper;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.piccolo.xml.XMLStreamReader;

/**
 * A <a href="http://camel.apache.org/type-coverter.html">Type Converter</a>
 * of XMLBeans objects
 */
@Converter
public final class XmlBeansConverter {

    private XmlBeansConverter() {
    }
    
    @Converter
    public static XmlObject toXmlObject(final File value, Exchange exchange) throws Exception {
        return (XmlObject) ObjectHelper.callWithTCCL(new Callable<XmlObject>() {
            public XmlObject call() throws Exception {
                return XmlObject.Factory.parse(value);
            }
        }, exchange); 
    }

    @Converter
    public static XmlObject toXmlObject(final Reader value, Exchange exchange) throws Exception {
        return (XmlObject) ObjectHelper.callWithTCCL(new Callable<XmlObject>() {
            public XmlObject call() throws Exception {
                return XmlObject.Factory.parse(value);
            }
        }, exchange);    
    }

    @Converter
    public static XmlObject toXmlObject(final Node value, Exchange exchange) throws Exception {
        return (XmlObject) ObjectHelper.callWithTCCL(new Callable<XmlObject>() {
            public XmlObject call() throws Exception {
                return XmlObject.Factory.parse(value);
            }
        }, exchange);    
    }

    @Converter
    public static XmlObject toXmlObject(final InputStream value, Exchange exchange) throws Exception {
        return (XmlObject) ObjectHelper.callWithTCCL(new Callable<XmlObject>() {
            public XmlObject call() throws Exception {
                return XmlObject.Factory.parse(value);
            }
        }, exchange);    
    }

    @Converter
    public static XmlObject toXmlObject(String value, Exchange exchange) throws Exception {
        return toXmlObject(IOConverter.toInputStream(value, exchange), exchange);
    }

    @Converter
    public static XmlObject toXmlObject(byte[] value, Exchange exchange) throws Exception {
        return toXmlObject(IOConverter.toInputStream(value), exchange);
    }

    @Converter
    public static XmlObject toXmlObject(ByteBuffer value, Exchange exchange) throws Exception {
        return toXmlObject(NIOConverter.toInputStream(value), exchange);
    }

    @Converter
    public static XmlObject toXmlObject(final XMLStreamReader value, Exchange exchange) throws Exception {
        return (XmlObject) ObjectHelper.callWithTCCL(new Callable<XmlObject>() {
            public XmlObject call() throws Exception {
                return XmlObject.Factory.parse(value);
            }
        }, exchange);    
    }

    @Converter
    public static XmlObject toXmlObject(Source value, Exchange exchange) throws Exception {
        final Reader reader = exchange.getContext().getTypeConverter().mandatoryConvertTo(Reader.class, value);
        return (XmlObject) ObjectHelper.callWithTCCL(new Callable<XmlObject>() {
            public XmlObject call() throws Exception {
                return XmlObject.Factory.parse(reader);
            }
        }, exchange);   
    }
}
