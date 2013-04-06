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
package org.apache.camel.component.xmlrpc.converter;

import java.util.List;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.xmlrpc.XmlRpcConstants;
import org.apache.camel.component.xmlrpc.XmlRpcRequestImpl;
import org.apache.xmlrpc.XmlRpcRequest;

@Converter
public final class XmlRpcConverter {
    
    private XmlRpcConverter() {
        //Helper class
    }
    
    @Converter
    public static XmlRpcRequest toXmlRpcRequest(final Object[] parameters, Exchange exchange) {
        // get the message operation name
        String operationName = exchange.getIn().getHeader(XmlRpcConstants.METHOD_NAME, String.class);
        
        // create the request object here
        XmlRpcRequest request = new XmlRpcRequestImpl(operationName, parameters);
        
        return request;
    }
    
    @Converter
    public static XmlRpcRequest toXmlRpcRequest(final List<?> parameters, Exchange exchange) {
        // get the message operation name
        String operationName = exchange.getIn().getHeader(XmlRpcConstants.METHOD_NAME, String.class);
        
        // create the request object here
        XmlRpcRequest request = new XmlRpcRequestImpl(operationName, parameters);
        
        return request;
    }

}
