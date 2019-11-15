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
package org.apache.camel.component.cxf.soap.headers;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Resource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.w3c.dom.Node;

import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.outofband.header.OutofBandHeader;

@javax.jws.WebService(serviceName = "HeaderService",
                      targetNamespace = "http://apache.org/camel/cxf/soap/headers",
                      endpointInterface = "org.apache.camel.component.cxf.soap.headers.HeaderTester")
                      
public class HeaderTesterImpl implements HeaderTester {
    
    @Resource
    protected WebServiceContext context;
    protected boolean relayHeaders = true;
    
    public HeaderTesterImpl() {
    }
    
    public HeaderTesterImpl(boolean relayHeaders) {
        this.relayHeaders = relayHeaders;
    }
    
    public void outHeader(OutHeader me, Holder<OutHeaderResponse> theResponse, Holder<SOAPHeaderData> headerInfo) { 
        try {
            OutHeaderResponse theResponseValue = new OutHeaderResponse();
            theResponseValue.setResponseType("pass");
            theResponse.value = theResponseValue;
            headerInfo.value = Constants.OUT_HEADER_DATA;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public InHeaderResponse inHeader(InHeader me, SOAPHeaderData headerInfo) { 
        try {
            InHeaderResponse result = new InHeaderResponse();
            if (!relayHeaders) {
                if (headerInfo == null) {
                    result.setResponseType("pass");
                } else {
                    result.setResponseType("fail");
                }
            } else if (Constants.equals(Constants.IN_HEADER_DATA, headerInfo)) {
                result.setResponseType("pass");
            } else {
                result.setResponseType("fail");
            }
            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public InoutHeaderResponse inoutHeader(InoutHeader me, Holder<SOAPHeaderData> headerInfo) { 
        try {
            InoutHeaderResponse result = new InoutHeaderResponse();
            if (!relayHeaders) {
                if (headerInfo.value == null) {
                    result.setResponseType("pass");
                } else {
                    result.setResponseType("fail");
                }
            } else if (Constants.equals(Constants.IN_OUT_REQUEST_HEADER_DATA, headerInfo.value)) {
                result.setResponseType("pass");
            } else {
                result.setResponseType("fail");
            }
            headerInfo.value = Constants.IN_OUT_RESPONSE_HEADER_DATA;
            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }
    
    public Me inOutOfBandHeader(Me me) { 
        try {
            Me result = new Me();
            if (validateOutOfBandHander()) {
                result.setFirstName("pass");
            } else {
                result.setFirstName("fail");
            }
            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public Me inoutOutOfBandHeader(Me me) { 
        try {
            Me result = new Me();
            if (validateOutOfBandHander()) {
                addReplyOutOfBandHeader();
                result.setFirstName("pass");
            } else {
                result.setFirstName("fail");
            }
            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public Me outOutOfBandHeader(Me me) { 
        try {
            Me result = new Me();
            result.setFirstName("pass");
            addReplyOutOfBandHeader();
            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    private void addReplyOutOfBandHeader() {
        if (context != null) {
            MessageContext ctx = context.getMessageContext();
            if (ctx != null) {
                try {
                    OutofBandHeader ob = new OutofBandHeader();
                    ob.setName("testOobReturnHeaderName");
                    ob.setValue("testOobReturnHeaderValue");
                    ob.setHdrAttribute("testReturnHdrAttribute");
                    JAXBElement<OutofBandHeader> job = new JAXBElement<>(
                            new QName(Constants.TEST_HDR_NS, Constants.TEST_HDR_RESPONSE_ELEM), 
                            OutofBandHeader.class, null, ob);
                    Header hdr = new Header(
                            new QName(Constants.TEST_HDR_NS, Constants.TEST_HDR_RESPONSE_ELEM), 
                            job, 
                            new JAXBDataBinding(ob.getClass()));
                    List<Header> hdrList = CastUtils.cast((List<?>) ctx.get(Header.HEADER_LIST));
                    hdrList.add(hdr);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    protected boolean validateOutOfBandHander() {
        MessageContext ctx = context == null ? null : context.getMessageContext();
        if (!relayHeaders) {
            if (ctx != null 
                && !ctx.containsKey(Header.HEADER_LIST)
                || (ctx.containsKey(Header.HEADER_LIST) 
                    && ((List<?>)ctx.get(Header.HEADER_LIST)).size() == 0)) {
                return true;
            }
            return false;
        }
        
        boolean success = false;
        if (ctx != null && ctx.containsKey(Header.HEADER_LIST)) {
            List<?> oobHdr = (List<?>) ctx.get(Header.HEADER_LIST);
            Iterator<?> iter = oobHdr.iterator();
            while (iter.hasNext()) {
                Object hdr = iter.next();
                if (hdr instanceof Header && ((Header) hdr).getObject() instanceof Node) {
                    Header hdr1 = (Header) hdr;
                    try {
                        JAXBElement<?> job = 
                            (JAXBElement<?>)JAXBContext.newInstance(org.apache.cxf.outofband.header.ObjectFactory.class)
                                .createUnmarshaller()
                                .unmarshal((Node) hdr1.getObject());
                        OutofBandHeader ob = (OutofBandHeader) job.getValue();
                        if ("testOobHeader".equals(ob.getName())
                            && "testOobHeaderValue".equals(ob.getValue())) { 
                            if ("testHdrAttribute".equals(ob.getHdrAttribute())) {
                                success = true;
                                iter.remove(); //mark it processed
                            } else if ("dontProcess".equals(ob.getHdrAttribute())) {
                                //we won't remove it so we won't let the runtime know
                                //it's processed.   It SHOULD throw an exception 
                                //saying the mustunderstand wasn't processed
                                success = true;
                            }
                        } else {
                            throw new RuntimeException("test failed");
                        }
                    } catch (JAXBException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } else {
            throw new RuntimeException("MessageContext is null or doesnot contain OOBHeaders");
        }
        
        return success;
    }    
}
