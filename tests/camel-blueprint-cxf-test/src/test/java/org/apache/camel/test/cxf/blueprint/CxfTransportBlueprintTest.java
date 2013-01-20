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
package org.apache.camel.test.cxf.blueprint;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class CxfTransportBlueprintTest extends CamelBlueprintTestSupport {
    
    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/cxf/blueprint/CxfTransportBeans.xml";
    }
    
    @Override
    // camel-cxf blueprint schema doesn't publihsed yet
    protected String getBundleDirectives() {
        return "blueprint.aries.xml-validation:=false";
    }
    
    @Test
    public void testPublishEndpointUrl() throws Exception {
        final String request = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body>"
            + "<ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<arg0 xmlns=\"http://cxf.component.camel.apache.org/\">hello world</arg0>"
            + "</ns1:echo></soap:Body></soap:Envelope>";
        String response = template.requestBody("direct:client", request, String.class);
        assertNotNull("We should get some response here", response);
        assertTrue("Get a wrong response.", response.indexOf("hello world") > 0 && response.indexOf("echoResponse") > 0);
    }


}
