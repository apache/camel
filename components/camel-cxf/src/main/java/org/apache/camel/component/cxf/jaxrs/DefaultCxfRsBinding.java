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

package org.apache.camel.component.cxf.jaxrs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.cxf.jaxrs.interceptor.JAXRSInInterceptor;
import org.apache.cxf.jaxrs.model.MethodInvocationInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfoStack;
import org.apache.cxf.jaxrs.model.URITemplate;

public class DefaultCxfRsBinding implements CxfRsBinding, HeaderFilterStrategyAware {
    private HeaderFilterStrategy headerFilterStrategy;
    
    public Object populateCxfRsResponseFromExchange(Exchange camelExchange,
                                                    org.apache.cxf.message.Exchange cxfExchange) throws Exception {
        if (camelExchange.isFailed()) {
            throw camelExchange.getException();
        }
        
        return camelExchange.getOut().getBody();
    }

    
    public void populateExchangeFromCxfRsRequest(org.apache.cxf.message.Exchange cxfExchange,
                                                 Exchange camelExchange, Method method, Object[] paramArray) {
        Message camelMessage = camelExchange.getIn();        
        //Copy the CXF message header into the Camel inMessage
        org.apache.cxf.message.Message cxfMessage = cxfExchange.getInMessage();
        
        copyMessageHeader(cxfMessage, camelMessage, org.apache.cxf.message.Message.REQUEST_URI, Exchange.HTTP_URI);
                
        copyMessageHeader(cxfMessage, camelMessage, JAXRSInInterceptor.RELATIVE_PATH, Exchange.HTTP_RELATIVE_PATH);
        
        copyMessageHeader(cxfMessage, camelMessage, org.apache.cxf.message.Message.HTTP_REQUEST_METHOD, Exchange.HTTP_METHOD);
        
        copyMessageHeader(cxfMessage, camelMessage, org.apache.cxf.message.Message.PATH_INFO, Exchange.HTTP_PATH);
        
        copyMessageHeader(cxfMessage, camelMessage, org.apache.cxf.message.Message.CONTENT_TYPE, Exchange.CONTENT_TYPE);
        
        copyMessageHeader(cxfMessage, camelMessage, org.apache.cxf.message.Message.ENCODING, Exchange.HTTP_CHARACTER_ENCODING);
        
        copyMessageHeader(cxfMessage, camelMessage, org.apache.cxf.message.Message.QUERY_STRING, Exchange.HTTP_QUERY);
        
        copyMessageHeader(cxfMessage, camelMessage, org.apache.cxf.message.Message.ACCEPT_CONTENT_TYPE, Exchange.ACCEPT_CONTENT_TYPE);
        
        camelMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, method.getReturnType());
               
        copyOperationResourceInfoStack(cxfMessage, camelMessage);
        
        camelMessage.setHeader(CxfConstants.OPERATION_NAME, method.getName());
        
        camelMessage.setHeader(CxfConstants.CAMEL_CXF_RS_METHOD, method);
        
        camelMessage.setBody(paramArray);        
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {        
        return headerFilterStrategy;
    }


    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;        
    }
    
    protected void copyMessageHeader(org.apache.cxf.message.Message cxfMessage, Message camelMessage, String cxfKey, String camelKey) {
        if (cxfMessage.get(cxfKey) != null) {
            camelMessage.setHeader(camelKey, cxfMessage.get(cxfKey));
        }
    }
    
    protected void copyOperationResourceInfoStack(org.apache.cxf.message.Message cxfMessage, Message camelMessage) {
        OperationResourceInfoStack stack = cxfMessage.get(OperationResourceInfoStack.class);
        if (stack != null) {
            // make a copy of the operation resource info for looking up the sub resource location
            OperationResourceInfoStack copyStack = (OperationResourceInfoStack)stack.clone();
            camelMessage.setHeader(CxfConstants.CAMEL_CXF_RS_OPERATION_RESOURCE_INFO_STACK, copyStack);
                        
        }
      
    }

}