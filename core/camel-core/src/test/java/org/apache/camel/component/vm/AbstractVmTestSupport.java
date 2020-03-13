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
package org.apache.camel.component.vm;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.After;
import org.junit.Before;

public abstract class AbstractVmTestSupport extends ContextTestSupport {

    protected CamelContext context2;
    protected ProducerTemplate template2;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        context2 = new DefaultCamelContext();
        template2 = context2.createProducerTemplate();

        startServices(template2, context2);

        // add routes after CamelContext has been started
        RouteBuilder routeBuilder = createRouteBuilderForSecondContext();
        if (routeBuilder != null) {
            context2.addRoutes(routeBuilder);
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        stopServices(context2, template2);
        VmComponent.ENDPOINTS.clear();
        VmComponent.QUEUES.clear();
        super.tearDown();
    }

    protected RouteBuilder createRouteBuilderForSecondContext() throws Exception {
        return null;
    }

    protected void startServices(Object... services) {
        for (Object o : services) {
            if (o instanceof Service) {
                ((Service) o).start();
            } else if (o instanceof CamelContext) {
                ((CamelContext) o).start();
            }
        }
    }

    protected void stopServices(Object... services) {
        for (Object o : services) {
            if (o instanceof Service) {
                ((Service) o).stop();
            } else if (o instanceof CamelContext) {
                ((CamelContext) o).stop();
            }
        }
    }

}
