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

import java.io.InputStream;
import java.net.URL;

import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfRsRouterTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {        
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringRouter.xml");
    }
    
    public void testGetCustomer() throws Exception {
        URL url = new URL("http://localhost:9000/customerservice/customers/123");

        InputStream in = url.openStream();
        assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", CxfUtils.getStringFromInputStream(in));
        // TODO we need to support the subResourceLocator
                
    }

}
