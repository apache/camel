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

import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.staxutils.StaxUtils;

/**
 * This is the base class for message interceptors that intercepts message as DOM content
 * infers the BindingOperationInfo and then set the
 *
 */
public abstract class AbstractMessageInInterceptor<T extends Message>
       extends AbstractPhaseInterceptor<T> {

    public AbstractMessageInInterceptor(String phase) {
        super(phase);
    }

    protected boolean isRequestor(Message message) {
        return Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE));
    }

    /**
     * Infer the OperationInfo from the XML Document and get the list of
     * parts as DOM Element
     */
    public void handleMessage(T message) throws Fault {
        Logger logger = getLogger();

        if (isFaultMessage(message)) {
            message.getInterceptorChain().abort();
            Endpoint ep = message.getExchange().get(Endpoint.class);
            if (ep.getInFaultObserver() != null) {
                ep.getInFaultObserver().onMessage(message);
                return;
            }
            //Fault f = createFault(message, payloadEl);
            //message.setContent(Exception.class, f);
            //return;
        }

        Document document = createDOMMessage(message);
        //Document document = message.getContent(Document.class);
        Element payloadEl = (Element)document.getChildNodes().item(0);

        Exchange ex = message.getExchange();
        BindingOperationInfo boi = ex.get(BindingOperationInfo.class);
        if (boi == null) {
            BindingInfo bi = ex.get(BindingInfo.class);
            if (bi == null) {
                Endpoint ep = ex.get(Endpoint.class);
                bi = ep.getEndpointInfo().getBinding();
                ex.put(BindingInfo.class, bi);
            }
            // handling inbound message
            if (logger.isLoggable(Level.INFO)) {
                logger.info("AbstractRoutingMessageInInterceptor Infer BindingOperationInfo.");
            }

            boi = getBindingOperation(message, document);

            if (boi == null) {
                QName startQName = new QName(payloadEl.getNamespaceURI(), payloadEl.getLocalName());

                throw new Fault(new org.apache.cxf.common.i18n.Message(
                                "REQ_NOT_UNDERSTOOD", getLogger(), startQName));
            }

            if (boi != null) {
                ex.put(BindingOperationInfo.class, boi);
                ex.put(OperationInfo.class, boi.getOperationInfo());
                ex.setOneWay(boi.getOperationInfo().isOneWay());
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("DOMInInterceptor- BindingOperation is:" + boi.getName());
                }
            }
        }

        BindingMessageInfo bmi = isRequestor(message) ?  boi.getOutput() : boi.getInput();
        List<Element> partList = getPartList(message, payloadEl, bmi);
        message.put(List.class, partList);

        Element header = getHeader(message);
        message.put(Element.class, header);
    }

    /**
     * This method is called to convert a incoming message format e.g Stax Stream
     * to a DOM Tree. Default Implementation converts Stax Stream to a DOM
     */
    protected Document createDOMMessage(T message) {
        Document doc = null;
        try {
            if (getLogger().isLoggable(Level.INFO)) {
                getLogger().info("AbstractMessageInInterceptor Converting Stax Stream to DOM");
            }
            XMLStreamReader xsr = message.getContent(XMLStreamReader.class);
            doc = StaxUtils.read(xsr);
        } catch (XMLStreamException xe) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("STAX_READ_EXC", getLogger()), xe);
        }
        return doc;
    }

    protected abstract Logger getLogger();

    /**
     * This method is called on incoming to check if it is a fault.
     */
    protected abstract boolean isFaultMessage(T message);

    /**
     * This method is called when the routing message interceptor has received a inbound message
     * It infers the binding operation by matching the root Element with a binding operation
     * from the service model.
     */
    protected abstract BindingOperationInfo getBindingOperation(T inMessage, Document document);

    /**
     * This method is called when the routing message interceptor has intercepted a inbound
     * message as a DOM Content.  It retreives the message parts as DOM Element
     * and returns a List of Element.
     */
    protected abstract List<Element> getPartList(T inMessage, Element rootElement, BindingMessageInfo boi);

    /**
     * This method is called when the routing message interceptor has intercepted a inbound
     * message as a DOM Content.  It retreives the header parts as DOM Element
     * and returns a Element.
     */
    protected abstract Element getHeader(T inMessage);

}
