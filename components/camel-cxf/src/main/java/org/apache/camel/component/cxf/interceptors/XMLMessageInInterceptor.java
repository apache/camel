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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.cxf.binding.xml.XMLConstants;
import org.apache.cxf.binding.xml.XMLFault;
import org.apache.cxf.bindings.xformat.XMLBindingMessageFormat;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.XMLMessage;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.staxutils.StaxUtils;

public class XMLMessageInInterceptor extends AbstractMessageInInterceptor<XMLMessage> {
    private static final Logger LOG = LogUtils.getL7dLogger(XMLMessageInInterceptor.class);
    

    public XMLMessageInInterceptor() {
        super(Phase.READ);        
    }

    protected Logger getLogger() {
        return LOG;
    }
    
    protected boolean isFaultMessage(XMLMessage message) {
        XMLStreamReader xsr = message.getContent(XMLStreamReader.class);
        boolean isFault = false;
        try {
            if (StaxUtils.skipToStartOfElement(xsr)) {
                QName startQName = xsr.getName();
                isFault = XMLConstants.NS_XML_FORMAT.equals(startQName.getNamespaceURI())
                          && XMLFault.XML_FAULT_ROOT.equals(startQName.getLocalPart());
            }
        } catch (XMLStreamException xse) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("STAX_READ_EXC", LOG));
        }
        
        return isFault;
    }
    
    protected BindingOperationInfo getBindingOperation(XMLMessage message, Document doc) {
        Exchange ex = message.getExchange();
        BindingInfo binding = ex.get(BindingInfo.class);
        if (binding == null) {
            Endpoint ep = ex.get(Endpoint.class);
            binding = ep.getEndpointInfo().getBinding();
        }
        //TODO if binding is null throw exception.

        Element payloadEl = (Element)doc.getChildNodes().item(0);
        QName startQName = new QName(payloadEl.getNamespaceURI(), payloadEl.getLocalName());

        // handling xml normal inbound message
        boolean client = isRequestor(message);

        List<BindingOperationInfo> boiList = new ArrayList<BindingOperationInfo>();
        for (BindingOperationInfo boi : binding.getOperations()) {
            BindingMessageInfo bmi = client ?  boi.getOutput() : boi.getInput();

            QName rootName = null;
            if (bmi != null) {
                XMLBindingMessageFormat msgFormat =
                    bmi.getExtensor(XMLBindingMessageFormat.class);

                if (msgFormat != null) {
                    rootName = msgFormat.getRootNode();
                } else {
                    Collection<MessagePartInfo> bodyParts = bmi.getMessageParts();
                    if (bodyParts.size() == 1) {
                        MessagePartInfo p = bodyParts.iterator().next();
                        rootName = p.getConcreteName();
                    }
                }
            }

            if (startQName.equals(rootName)) {
                boiList.add(boi);
            }
        }
        
        BindingOperationInfo match = null;
        if (boiList.size() > 1) {
            if (LOG.isLoggable(Level.INFO)) {
                LOG.info("Mulitple matching BindingOperationIno found in Binding.");
            }
        } else if (!boiList.isEmpty()) {
            match = boiList.get(0);
        }
        return match; 
    }
    
    protected List<Element> getPartList(XMLMessage inMessage, Element rootNode, BindingMessageInfo bmi) {
        List<Element> partList = new ArrayList<Element>();
        XMLBindingMessageFormat msgFormat =
            bmi.getExtensor(XMLBindingMessageFormat.class);
        if (msgFormat != null) {
            NodeList nodeList = rootNode.getChildNodes();
            for (int idx = 0; idx < nodeList.getLength(); idx++) {
                partList.add((Element)nodeList.item(idx));
            }
        } else {
            partList.add(rootNode);
        }
        return partList;
    }
    
}
