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
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test to verify using '#' notation to reference serviceClass.
 */
@ContextConfiguration
@ExtendWith(SpringExtension.class)
public class ServiceClassRefTest {

    private static AvailablePortFinder.Port PORT1;
    private static AvailablePortFinder.Port PORT2;
    private static AvailablePortFinder.Port PORT3;
    private static AvailablePortFinder.Port PORT4;

    @BeforeAll
    static void allocateServiceClassRefTestPorts() {
        PORT1 = AvailablePortFinder.find();
        System.setProperty("ServiceClassRefTest.port1", PORT1.toString());
        PORT2 = AvailablePortFinder.find();
        System.setProperty("ServiceClassRefTest.port2", PORT2.toString());
        PORT3 = AvailablePortFinder.find();
        System.setProperty("ServiceClassRefTest.port3", PORT3.toString());
        PORT4 = AvailablePortFinder.find();
        System.setProperty("ServiceClassRefTest.port4", PORT4.toString());
    }

    @AfterAll
    static void releaseServiceClassRefTestPorts() {
        System.clearProperty("ServiceClassRefTest.port1");
        if (PORT1 != null) {
            PORT1.release();
        }
        System.clearProperty("ServiceClassRefTest.port2");
        if (PORT2 != null) {
            PORT2.release();
        }
        System.clearProperty("ServiceClassRefTest.port3");
        if (PORT3 != null) {
            PORT3.release();
        }
        System.clearProperty("ServiceClassRefTest.port4");
        if (PORT4 != null) {
            PORT4.release();
        }
    }

    @Autowired
    protected CamelContext context;

    @Test
    public void testServiceClassNameCreatedByRefNotation() throws Exception {
        // verify the '#' notation works
        CxfEndpoint endpoint = context.getEndpoint("cxf:bean:fromEndpoint", CxfEndpoint.class);
        assertEquals("org.apache.camel.component.cxf.HelloServiceImpl",
                endpoint.getServiceClass().getName());
        assertEquals(DataFormat.POJO, endpoint.getDataFormat());

        // verify values in bean properties are ok
        endpoint = context.getEndpoint("cxf:bean:fromEndpointWithProps", CxfEndpoint.class);
        assertEquals(DataFormat.PAYLOAD, endpoint.getDataFormat());
    }

}
