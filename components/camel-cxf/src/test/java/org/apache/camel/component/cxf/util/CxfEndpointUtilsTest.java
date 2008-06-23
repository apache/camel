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

import javax.xml.namespace.QName;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CxfComponent;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.DataFormat;
import org.apache.camel.impl.DefaultCamelContext;

public class CxfEndpointUtilsTest extends TestCase {
    // set up the port name and service name
    protected static final QName SERVICE_NAME =
        new QName("http://www.example.com/test", "ServiceName");


    private static final String CXF_BASE_URI = "cxf://http://www.example.com/testaddress"
        + "?serviceClass=org.apache.camel.component.cxf.HelloService"
        + "&portName={http://www.example.com/test}PortName"
        + "&serviceName={http://www.example.com/test}ServiceName"
        + "&setDefaultBus=true";



    protected String getEndpointURI() {
        return CXF_BASE_URI;
    }

    protected CamelContext getCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    protected CxfEndpoint createEndpoint(String uri) throws Exception {
        CamelContext context = getCamelContext();
        return (CxfEndpoint)new CxfComponent(context).createEndpoint(uri);
    }

    public void testGetProperties() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getEndpointURI());
        QName service = CxfEndpointUtils.getQName(endpoint.getServiceName());
        assertEquals("We should get the right service name", service, SERVICE_NAME);
        assertEquals("We should get the setDefaultBus value", CxfEndpointUtils.getSetDefaultBus(endpoint) , true);
    }

    public void testGetDataFormat() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getEndpointURI() + "&dataFormat=MESSAGE");
        assertEquals("We should get the Message DataFormat", CxfEndpointUtils.getDataFormat(endpoint), DataFormat.MESSAGE);
    }



}
