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
package org.apache.camel.web.resources;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.apache.camel.web.Main;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class TestSupport extends Assert {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected ClientConfig clientConfig;
    protected Client client;
    protected WebResource resource;

    @Before
    public void setUp() throws Exception {
        Main.start();

        clientConfig = new DefaultClientConfig();
        // use the following jaxb context resolver
        //cc.getProviderClasses().add(JAXBContextResolver.class);
        client = Client.create(clientConfig);
        resource = client.resource(Main.getRootUrl());
    }

    @After
    public void tearDown() throws Exception {
        Main.stop();
    }


    protected WebResource resource(String uri) {
        log.info("About to test URI: " + uri);
        return resource.path(uri);
    }
    protected void assertHtmlResponse(String response) {
        assertNotNull("No text returned!", response);

        assertResponseContains(response, "<html>");
        assertResponseContains(response, "</html>");
    }

    protected void assertResponseContains(String response, String text) {
        assertTrue("Response should contain " + text + " but was: " + response, response.contains(text));
    }
}
