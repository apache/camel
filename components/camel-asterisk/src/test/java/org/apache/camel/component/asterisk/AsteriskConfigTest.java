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
package org.apache.camel.component.asterisk;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsteriskConfigTest extends CamelTestSupport {

    private String hostname = "192.168.0.254";
    private String username = "username";
    private String password = "password";
    private String action = "QUEUE_STATUS";

    @Test
    void asteriskEndpointData() {
        Endpoint endpoint = context.getEndpoint("asterisk://myVoIP?hostname=" + hostname + "&username=" + username + "&password=" + password + "&action=" + action);
        assertTrue(endpoint instanceof AsteriskEndpoint, "Endpoint not an AsteriskEndpoint: " + endpoint);
        AsteriskEndpoint asteriskEndpoint = (AsteriskEndpoint)endpoint;

        assertEquals(hostname, asteriskEndpoint.getHostname());
        assertEquals(username, asteriskEndpoint.getUsername());
        assertEquals(password, asteriskEndpoint.getPassword());
        assertEquals(action, asteriskEndpoint.getAction().name());
    }
}
