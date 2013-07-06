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

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.common.TypeFactory;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;


public class XmlRpcWriter extends org.apache.xmlrpc.serializer.XmlRpcWriter {
    private static final Attributes ZERO_ATTRIBUTES = new AttributesImpl();
    private final ContentHandler handler;

    public XmlRpcWriter(XmlRpcStreamConfig pConfig, ContentHandler pHandler, TypeFactory pTypeFactory) {
        super(pConfig, pHandler, pTypeFactory);
        handler = pHandler;
    }
    
    public void writeRequest(XmlRpcStreamConfig config, XmlRpcRequest request) throws SAXException {
        handler.startDocument();
        boolean extensions = config.isEnabledForExtensions();
        if (extensions) {
            handler.startPrefixMapping("ex", org.apache.xmlrpc.serializer.XmlRpcWriter.EXTENSIONS_URI);
        }
        handler.startElement("", "methodCall", "methodCall", ZERO_ATTRIBUTES);
        handler.startElement("", "methodName", "methodName", ZERO_ATTRIBUTES);
        String s = request.getMethodName();
        handler.characters(s.toCharArray(), 0, s.length());
        handler.endElement("", "methodName", "methodName");
        handler.startElement("", "params", "params", ZERO_ATTRIBUTES);
        int num = request.getParameterCount();
        for (int i = 0; i < num; i++) {
            handler.startElement("", "param", "param", ZERO_ATTRIBUTES);
            writeValue(request.getParameter(i));
            handler.endElement("", "param", "param");
        }
        handler.endElement("", "params", "params");
        handler.endElement("", "methodCall", "methodCall");
        if (extensions) {
            handler.endPrefixMapping("ex");
        }
        handler.endDocument();
    }

}
