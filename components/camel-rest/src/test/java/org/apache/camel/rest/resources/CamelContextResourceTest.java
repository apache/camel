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
package org.apache.camel.rest.resources;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import junit.framework.TestCase;
import org.apache.camel.rest.Main;
import org.apache.camel.rest.model.EndpointLink;
import org.apache.camel.rest.model.Endpoints;

import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public class CamelContextResourceTest extends TestCase {
    protected int port = 9998;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Main.run(port);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        Main.stop();
    }

    public void testMain() throws Exception {
        ClientConfig cc = new DefaultClientConfig();
        // use the following jaxb context resolver
        //cc.getProviderClasses().add(JAXBContextResolver.class);
        Client c = Client.create(cc);

        WebResource wr = c.resource("http://localhost:" + port + Main.WEBAPP_CTX);

        // get the initial representation

        Endpoints endpoints = wr.path("camel/endpoints").accept("application/xml").get(Endpoints.class);

        // and print it out
        System.out.println("Found: " + endpoints.getEndpoints());

        List<EndpointLink> list = endpoints.getEndpoints();
        assertTrue("Should have received some endpoints!", !list.isEmpty());
    }
}