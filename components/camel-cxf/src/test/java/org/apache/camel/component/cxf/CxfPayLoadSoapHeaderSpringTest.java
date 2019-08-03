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

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Before;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfPayLoadSoapHeaderSpringTest extends CxfPayLoadSoapHeaderTest {
    protected AbstractXmlApplicationContext applicationContext;
    
    @Override
    protected String getRouterEndpointURI() {
        return "cxf:bean:routerEndpoint?dataFormat=PAYLOAD";
    }
    @Override
    protected String getServiceEndpointURI() {
        return "cxf:bean:serviceEndpoint?dataFormat=PAYLOAD";
    }
   
    @Override
    @Before
    public void setUp() throws Exception {
        applicationContext = createApplicationContext();
        super.setUp();
        assertNotNull("Should have created a valid spring context", applicationContext);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        IOHelper.close(applicationContext);
        super.tearDown();
    }
      
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext, true);
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/PizzaEndpoints.xml");
    }
    
    

}
