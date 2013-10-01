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
package org.apache.camel.component.vm;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ServiceHelper;
import org.junit.After;
import org.junit.Before;

/**
 * @version 
 */
public abstract class AbstractVmTestSupport extends ContextTestSupport {
    
    protected CamelContext context2;
    protected ProducerTemplate template2;
    
    @Override
    @Before
    protected void setUp() throws Exception {
        super.setUp();
        context2 = new DefaultCamelContext();
        template2 = context2.createProducerTemplate();
        
        ServiceHelper.startServices(template2, context2);

        // add routes after CamelContext has been started
        RouteBuilder routeBuilder = createRouteBuilderForSecondContext();
        if (routeBuilder != null) {
            context2.addRoutes(routeBuilder);
        }
    }
    
    @Override
    @After
    protected void tearDown() throws Exception {
        ServiceHelper.stopServices(context2, template2);
        VmComponent.ENDPOINTS.clear();
        VmComponent.QUEUES.clear();
        super.tearDown();
    }
    
    protected RouteBuilder createRouteBuilderForSecondContext() throws Exception {
        return null;
    }
}