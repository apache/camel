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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.w3c.dom.Element;

import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.StaxUtils;

/**
 * Interceptor to create a Fault object from a CXF message
 * 
 * @version @Revision: 789534 $
 */
public class PayloadFaultInInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger LOG = Logger.getLogger(PayloadFaultInInterceptor.class.getName());

    public PayloadFaultInInterceptor() {
        this(Phase.POST_PROTOCOL);
    }

    public PayloadFaultInInterceptor(String phase) {
        super(phase);
    }

    public void handleMessage(Message message) throws Fault {
        XMLStreamReader reader = message.getContent(XMLStreamReader.class);
        
        if (message instanceof SoapMessage) {
            message.setContent(Exception.class, createFault((SoapMessage)message, reader));
        } else {       
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Message type '" + message.getClass().getName() + "' is not supported.");
            }
        }
    }

    public static SoapFault createFault(SoapMessage message, 
                                           XMLStreamReader reader) {
        String exMessage = null;
        QName faultCode = null;
        String role = null;
        Element detail = null;
        
        try {
            while (reader.nextTag() == XMLStreamReader.START_ELEMENT) {
                if (reader.getLocalName().equals("faultcode")) {
                    faultCode = StaxUtils.readQName(reader);
                } else if (reader.getLocalName().equals("faultstring")) {
                    exMessage = reader.getElementText();
                } else if (reader.getLocalName().equals("faultactor")) {
                    role = reader.getElementText();
                } else if (reader.getLocalName().equals("detail")) {
                    detail = StaxUtils.read(reader).getDocumentElement();
                }
            }
        } catch (XMLStreamException e) {
            throw new SoapFault("Could not parse message.",
                                e,
                                message.getVersion().getSender());
        }

        SoapFault fault = new SoapFault(exMessage, faultCode);
        fault.setDetail(detail);
        fault.setRole(role);
        return fault;
    }

}
