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
package org.apache.camel.spring.config;

import javax.annotation.Resource;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

/**
 * @version 
 */
@ContextConfiguration
public class EndpointUriSetFromSpringTest extends SpringRunWithTestSupport {

    @Resource(name = "foo:bar")
    MockEndpoint endpoint;

    @Test
    public void testEndpointCreatedWithCorrectUri() throws Exception {
        assertNotNull("foo", endpoint);
        assertEquals("foo.getEndpointUri()", "foo:bar", endpoint.getEndpointUri());
        log.info("Found endpoint " + endpoint + " with URI: " + endpoint.getEndpointUri());
    }

}
