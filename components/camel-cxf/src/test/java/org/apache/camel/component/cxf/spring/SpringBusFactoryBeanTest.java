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
package org.apache.camel.component.cxf.spring;

import org.apache.camel.component.cxf.transport.CamelTransportFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.soap.SoapBindingFactory;
import org.apache.cxf.version.Version;
import org.junit.Test;

public class SpringBusFactoryBeanTest extends AbstractSpringBeanTestSupport {

    @Override
    protected String[] getApplicationContextFiles() {
        return new String[]{"org/apache/camel/component/cxf/spring/SpringBusFactoryBeans.xml"};
    }
    
    @Test
    public void getTheBusInstance() {
        Bus bus = (Bus)ctx.getBean("cxfBus");
        assertNotNull("The bus should not be null", bus);
        if (Version.getCurrentVersion().startsWith("2.3")) {
            // This test just for the CXF 2.3.x, we skip this test with CXF 2.4.x
            CamelTransportFactory factory = bus.getExtension(CamelTransportFactory.class);
            assertNull("You should not find the factory here", factory);
        }
        
        bus = (Bus)ctx.getBean("myBus");
        assertNotNull("The bus should not be null", bus);

        CamelTransportFactory factory = bus.getExtension(CamelTransportFactory.class);
        assertNotNull("You should find the factory here", factory);
        SoapBindingFactory soapBindingFactory = bus.getExtension(SoapBindingFactory.class);
        assertNotNull("You should find the factory here", soapBindingFactory);
    }

}
