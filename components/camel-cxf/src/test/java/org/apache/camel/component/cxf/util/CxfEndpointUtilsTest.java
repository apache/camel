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
package org.apache.camel.component.cxf.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CxfComponent;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.easymock.classextension.EasyMock;
import sun.nio.cs.ThreadLocalCoders;
import sun.text.Normalizer;
import junit.framework.TestCase;

public class CxfEndpointUtilsTest extends TestCase {
    // set up the port name and service name
    private static final QName SERVICE_NAME =
        new QName("http://www.example.com/test", "ServiceName");
   
    static final String CXF_BASE_URI = "cxf://http://www.example.com/testaddress"
        + "?serviceClass=org.apache.camel.component.cxf.HelloService"
        + "&portName={http://www.example.com/test}PortName" 
        + "&serviceName={http://www.example.com/test}ServiceName";
        
    
    CxfEndpoint cxfEndpoint;
        
    protected void createEndpoint(String uri) throws Exception {
        CamelContext context = new DefaultCamelContext();
        cxfEndpoint = (CxfEndpoint)new CxfComponent(context).createEndpoint(uri);
    }
    
    public void testGetQName() throws Exception {
        createEndpoint(CXF_BASE_URI);
        QName service = CxfEndpointUtils.getQName(cxfEndpoint.getServiceName());
        assertEquals("We should get the right service name", service, SERVICE_NAME);        
    }
    
   
      
}
