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
package org.apache.camel.dataformat.xmlrpc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.apache.camel.Exchange;
import org.apache.camel.component.xmlrpc.XmlRpcConstants;
import org.apache.camel.component.xmlrpc.XmlRpcRequestImpl;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.ws.commons.serialize.CharSetXMLWriter;
import org.apache.ws.commons.serialize.XMLWriter;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.client.XmlRpcClientException;
import org.apache.xmlrpc.common.TypeFactory;
import org.apache.xmlrpc.common.TypeFactoryImpl;
import org.apache.xmlrpc.common.XmlRpcHttpRequestConfigImpl;
import org.apache.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.apache.xmlrpc.parser.XmlRpcRequestParser;
import org.apache.xmlrpc.parser.XmlRpcResponseParser;
import org.apache.xmlrpc.util.SAXParsers;

public class XmlRpcDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    private XmlRpcStreamRequestConfig xmlRpcStreamRequestConfig = new XmlRpcHttpRequestConfigImpl();
    private TypeFactory typeFactory = new TypeFactoryImpl(null);
    private boolean isRequest;
    
    protected XMLWriter getXMLWriter(Exchange exchange, OutputStream outputStream) throws XmlRpcException {
        XMLWriter writer = new CharSetXMLWriter();
        String encoding = IOHelper.getCharsetName(exchange);
        writer.setEncoding(encoding);
        writer.setIndenting(false);
        writer.setFlushing(true);
        try {
            writer.setWriter(new BufferedWriter(new OutputStreamWriter(outputStream, encoding)));
        } catch (UnsupportedEncodingException e) {
            throw new XmlRpcException("Unsupported encoding: " + encoding, e);
        }
        return writer;
    }

    @Override
    public String getDataFormatName() {
        return "xmlrpc";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        // need to check the object type
        XMLWriter control = getXMLWriter(exchange, stream);
        XmlRpcWriter writer = new XmlRpcWriter(xmlRpcStreamRequestConfig, control, typeFactory);

        XmlRpcRequest request = null;
        if (isRequest || graph instanceof XmlRpcRequest) {
            request = exchange.getContext().getTypeConverter().mandatoryConvertTo(XmlRpcRequest.class, exchange, graph);
        }

        if (request != null) {
            writer.writeRequest(xmlRpcStreamRequestConfig, request);
        } else {
            // write the result here directly
            // TODO write the fault message here
            writer.write(xmlRpcStreamRequestConfig, graph);
        }
    }

    protected int getErrorCode(Exchange exchange) {
        return exchange.getIn().getHeader(XmlRpcConstants.ERROR_CODE, int.class);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        if (isRequest) {
            return unmarshalRequest(exchange, stream);
        } else {
            return unmarshalResponse(exchange, stream);
        }
    }
    
    protected Object unmarshalResponse(Exchange exchange, InputStream stream) throws Exception {
        InputSource isource = new InputSource(stream);
        XMLReader xr = newXMLReader();
        XmlRpcResponseParser xp;
        try {
            xp = new XmlRpcResponseParser(xmlRpcStreamRequestConfig, typeFactory);
            xr.setContentHandler(xp);
            xr.parse(isource);
        } catch (SAXException e) {
            throw new XmlRpcClientException("Failed to parse server's response: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XmlRpcClientException("Failed to read server's response: " + e.getMessage(), e);
        }
        if (xp.isSuccess()) {
            return xp.getResult();
        }
        Throwable t = xp.getErrorCause();
        if (t == null) {
            throw new XmlRpcException(xp.getErrorCode(), xp.getErrorMessage());
        }
        if (t instanceof XmlRpcException) {
            throw (XmlRpcException)t;
        }
        if (t instanceof RuntimeException) {
            throw (RuntimeException)t;
        }
        throw new XmlRpcException(xp.getErrorCode(), xp.getErrorMessage(), t);

    }
    
    protected Object unmarshalRequest(Exchange exchange, InputStream stream) throws Exception {
        InputSource isource = new InputSource(stream);
        XMLReader xr = newXMLReader();
        XmlRpcRequestParser xp;
        try {
            xp = new XmlRpcRequestParser(xmlRpcStreamRequestConfig, typeFactory);
            xr.setContentHandler(xp);
            xr.parse(isource);
        } catch (SAXException e) {
            throw new XmlRpcClientException("Failed to parse server's response: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XmlRpcClientException("Failed to read server's response: " + e.getMessage(), e);
        }
        return new XmlRpcRequestImpl(xp.getMethodName(), xp.getParams());

    }
    
    protected XMLReader newXMLReader() throws XmlRpcException {
        return SAXParsers.newXMLReader();
    }

    public boolean isRequest() {
        return isRequest;
    }

    public void setRequest(boolean isRequest) {
        this.isRequest = isRequest;
    }
    
    public void setXmlRpcStreamRequestConfig(XmlRpcStreamRequestConfig config) {
        this.xmlRpcStreamRequestConfig = config;
    }
    
    public XmlRpcStreamRequestConfig getXmlRpcStreamRequestConfig() {
        return xmlRpcStreamRequestConfig;
    }
    
    public void setTypeFactory(TypeFactory typeFactory) {
        this.typeFactory = typeFactory;
    }
    
    public TypeFactory getTypeFactory() {
        return typeFactory;
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
