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
package org.apache.camel.component.cxf.soap.headers;

import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.ws.handler.MessageContext;

import org.w3c.dom.Node;

import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.outofband.header.OutofBandHeader;


public class HeaderTesterWithInsertionImpl extends HeaderTesterImpl {
    
    @Override
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
            List<Header> oobHdr = CastUtils.cast((List<?>) ctx.get(Header.HEADER_LIST));
            if (oobHdr.size() != 2) {
                throw new RuntimeException("test failed expected 2 soap headers but found " + oobHdr.size());
            }
            verifyHeader(oobHdr.get(0), "testOobHeader", "testOobHeaderValue");
            verifyHeader(oobHdr.get(1), "New_testOobHeader", "New_testOobHeaderValue");
            oobHdr.clear();
            success = true;
        } else {
            throw new RuntimeException("MessageContext is null or doesnot contain OOBHeaders");
        }
        
        return success;
    }

    private void verifyHeader(Object hdr, String headerName, String headerValue) {
        if (hdr instanceof Header && ((Header) hdr).getObject() instanceof Node) {
            Header hdr1 = (Header) hdr;
            try {
                JAXBElement<?> job = 
                    (JAXBElement<?>)JAXBContext.newInstance(org.apache.cxf.outofband.header.ObjectFactory.class)
                        .createUnmarshaller()
                        .unmarshal((Node) hdr1.getObject());
                OutofBandHeader ob = (OutofBandHeader) job.getValue();
                if (!headerName.equals(ob.getName())) {
                    throw new RuntimeException("test failed expected name ' + headerName + ' but found '"
                                               + ob.getName() + "'");
                }
                
                if (!headerValue.equals(ob.getValue())) {
                    throw new RuntimeException("test failed expected name ' + headerValue + ' but found '"
                                               + ob.getValue() + "'");
                }
            } catch (JAXBException ex) {
                throw new RuntimeException("test failed", ex);
            }
        } else {
            throw new RuntimeException("test failed. Unexpected type " + hdr.getClass());
        }
        
    }        
    
    

}
