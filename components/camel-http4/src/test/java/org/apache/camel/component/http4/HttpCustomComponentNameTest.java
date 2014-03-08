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
package org.apache.camel.component.http4;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ServiceHelper;
import org.junit.Test;

public class HttpCustomComponentNameTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testCustomName() throws Exception {
        context.start();

        int port = AvailablePortFinder.getNextAvailable(24400);

        Component custom = new HttpComponent();
        context.addComponent("http-foo", custom);
        ServiceHelper.startService(custom);

        String uri = "http-foo://www.somewhere.com:" + port + "?q=Camel";
        Endpoint endpoint = context.getEndpoint(uri);
        assertNotNull(endpoint);
        // the endpoint uri should use the custom component name as scheme
        assertEquals(uri, endpoint.getEndpointUri());

        context.stop();
    }

}
