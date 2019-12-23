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
package org.apache.camel.component.cxf;

import org.apache.camel.builder.RouteBuilder;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("As the refelection can't tell the paramenter name from SEI without annonation, "
    + "CXF cannot send a meaningful request for unwrapped message."
    + " We need to use the annontated SEI for testing")
public class CxfSimpleRouterWithUnwrappedStyleTest extends CxfSimpleRouterTest {    
   
    private String routerEndpointURI = "cxf://" + getRouterAddress() + "?" + SERVICE_CLASS + "&wrappedStyle=false";
    private String serviceEndpointURI = "cxf://" + getServiceAddress() + "?" + SERVICE_CLASS + "&wrappedStyle=false";
    
    
    @Override
    protected void configureFactory(ServerFactoryBean svrBean) {
        svrBean.getServiceFactory().setWrapped(false);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());
                from(routerEndpointURI).to("log:org.apache.camel?level=DEBUG").to(serviceEndpointURI);
            }
        };
    }
    
    @Override
    protected HelloService getCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(getRouterAddress());
        clientBean.setServiceClass(HelloService.class); 
        clientBean.getServiceFactory().setWrapped(false);
        HelloService client = (HelloService) proxyFactory.create();
        return client;
    }
    
    @Override
    @Test
    public void testOnwayInvocation() throws Exception {
        // ignore the invocation without parameter, as the document-literal doesn't support the invocation without parameter.
    }
    
    @Override
    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {        
        HelloService client = getCXFClient();
        Boolean result = client.echoBoolean(true);
        assertEquals("we should get the right answer from router", true, result);
        // The below invocation is failed with CXF 2.6.1 as the request are all start with <arg0>
        String str = client.echo("hello world");
        assertEquals("we should get the right answer from router", "echo hello world", str);

    }

}
