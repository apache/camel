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
package org.apache.camel.component.cxf.interceptors;

import java.io.OutputStream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Document;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.StaxUtils;


public class RawMessageWSDLGetOutInterceptor extends AbstractPhaseInterceptor<Message> {
    public static final RawMessageWSDLGetOutInterceptor INSTANCE = new RawMessageWSDLGetOutInterceptor();

    public RawMessageWSDLGetOutInterceptor() {
        super(Phase.PRE_STREAM);
    }

    public void handleMessage(Message message) throws Fault {

        Document doc = (Document)message.get(RawMessageWSDLGetInterceptor.DOCUMENT_HOLDER);
        if (doc == null) {
            return;
        }
        message.remove(RawMessageWSDLGetInterceptor.DOCUMENT_HOLDER);

        OutputStream out = message.getContent(OutputStream.class);

        String enc = null;
        try {
            enc = doc.getXmlEncoding();
        } catch (Exception ex) {
            //ignore - not dom level 3
        }
        if (enc == null) {
            enc = "utf-8";
        }

        XMLStreamWriter writer = StaxUtils.createXMLStreamWriter(out, enc);
        try {
            StaxUtils.writeNode(doc, writer, true);
            writer.flush();
        } catch (XMLStreamException e) {
            throw new Fault(e);
        }

    }
}
