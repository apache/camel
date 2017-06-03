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
package org.apache.camel.component.google.drive;



import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.drive.internal.DriveFilesApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for com.google.api.services.drive.Drive$Files APIs.
 */
public class CustomClientFactoryTest extends AbstractGoogleDriveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CustomClientFactoryTest.class);
    private static final String PATH_PREFIX = GoogleDriveApiCollection.getCollection().getApiName(DriveFilesApiMethod.class).getName();
    
    @Test
    public void testClientFactoryUpdated() throws Exception {
        Endpoint endpoint = context.getEndpoint("google-drive://drive-files/list?clientFactory=#myAuth");
        assertTrue(endpoint instanceof GoogleDriveEndpoint);
        assertTrue(((GoogleDriveEndpoint)endpoint).getClientFactory() instanceof MyClientFactory);        
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("myAuth", new MyClientFactory());
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {           
                from("google-drive://drive-files/list?clientFactory=#myAuth").to("mock:result");
            }
        };
    }
}
