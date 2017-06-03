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

import org.apache.camel.component.cxf.jaxrs.CxfRsEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.junit.Test;

public class CxfRsEndpointBeansTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/cxf/blueprint/CxfRsEndpointBeans.xml";
    }

    @Override
    protected String getBundleDirectives() {
        return "blueprint.aries.xml-validation:=false";
    }

    @Test
    public void testCxfBusInjection() {

        CxfRsEndpoint serviceEndpoint = context.getEndpoint("cxfrs:bean:serviceEndpoint", CxfRsEndpoint.class);
        CxfRsEndpoint routerEndpoint = context.getEndpoint("cxfrs:bean:routerEndpoint", CxfRsEndpoint.class);
        JAXRSServerFactoryBean server = routerEndpoint.createJAXRSServerFactoryBean();
        JAXRSClientFactoryBean client = serviceEndpoint.createJAXRSClientFactoryBean();
        assertEquals("These cxfrs endpoints don't share the same bus", server.getBus().getId(), client.getBus().getId());
    }
    


}
