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
package org.apache.camel.rest.resources;

import junit.framework.TestCase;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

import org.apache.camel.rest.Main;

/**
 * @version $Revision$
 */
public class TestSupport extends TestCase {
    protected int port = 9998;
    protected ClientConfig clientConfig;
    protected Client client;
    protected WebResource resource;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Main.run(port);

        clientConfig = new DefaultClientConfig();
        // use the following jaxb context resolver
        //cc.getProviderClasses().add(JAXBContextResolver.class);
        client = Client.create(clientConfig);
        resource = client.resource("http://localhost:" + port + Main.WEBAPP_CTX);
    }

    protected WebResource resource(String uri) {
        System.out.println("About to test URI: " + uri);
        return resource.path(uri);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        Main.stop();
    }
}
