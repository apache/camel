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
package org.apache.camel.component.google.drive;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for com.google.api.services.drive.Drive$Files APIs.
 */
public class CustomClientFactoryTest extends AbstractGoogleDriveTestSupport {

    @BindToRegistry("myAuth")
    private MyClientFactory cf = new MyClientFactory();

    @Test
    public void testClientFactoryUpdated() throws Exception {
        Endpoint endpoint = context.getEndpoint("google-drive://drive-files/list?clientFactory=#myAuth");
        assertTrue(endpoint instanceof GoogleDriveEndpoint);
        assertTrue(((GoogleDriveEndpoint) endpoint).getClientFactory() instanceof MyClientFactory);
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
