/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ProducerCache;
import org.apache.camel.util.CamelClient;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.builder.RouteBuilder;

/**
 * A useful base class which creates a {@link CamelContext} with some routes along with a {@link CamelClient}
 * for use in the test case
 *
 * @version $Revision: 1.1 $
 */
public abstract class ContextTestSupport extends TestSupport {
    protected CamelContext context;
    protected CamelClient<Exchange> client;

    @Override
    protected void setUp() throws Exception {
        context = createCamelContext();
        client = new CamelClient<Exchange>(context);

        context.addRoutes(createRouteBuilder());

        context.start();
    }

    @Override
    protected void tearDown() throws Exception {
        client.stop();
        context.stop();
    }

    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    protected abstract RouteBuilder createRouteBuilder();

    protected Endpoint<Exchange> resolveMandatoryEndpoint(String uri) {
        return resolveMandatoryEndpoint(context, uri);
    }
}
