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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.xmlrpc.XmlRpcConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.xmlrpc.XmlRpcRequest;
import org.junit.Assert;
import org.junit.Test;

public class XmlRpcConverterTest extends Assert {
    
    @Test
    public void testToXmlRpcRequest() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(XmlRpcConstants.METHOD_NAME, "greet");
        exchange.getIn().setBody(new Object[] {"me", "you"});
        XmlRpcRequest request = exchange.getIn().getBody(XmlRpcRequest.class);
        
        assertNotNull("The request should not be null", request);
        assertEquals("Get a wrong operation name", "greet", request.getMethodName());
        assertEquals("Get a wrong parameter size", 2, request.getParameterCount());
        assertEquals("Get a worng parameter", "you", request.getParameter(1));
    }
    
    @Test(expected = TypeConversionException.class)
    public void testToXmlRpcRequestWithoutOperationName() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        
        exchange.getIn().setBody(new Object[] {"me", "you"});
        exchange.getIn().getBody(XmlRpcRequest.class);
        fail("Expect the exception is throw");
        
    }

}
