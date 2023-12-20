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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.junit.jupiter.api.Disabled;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class UndertowCxfRsRouterTest extends CxfRsRouterTest {
    private static final int PORT2 = CXFTestSupport.getPort5();

    @Override
    protected int getPort() {
        return PORT2;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/UndertowCxfRsSpringRouter.xml");
    }

    @Override
    @Disabled("The test from the parent class is not applicable in this scenario")
    public void testEndpointUris() {
        // Don't test anything here
    }

}
