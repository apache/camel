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
package org.apache.camel.component.pubnub;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PubNubConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithIllegalArguments() {
        PubNubComponent component = context.getComponent("pubnub", PubNubComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("pubnub"));
    }

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        PubNubComponent component = context.getComponent("pubnub", PubNubComponent.class);
        PubNubEndpoint endpoint = (PubNubEndpoint) component.createEndpoint("pubnub:xxx?subscribeKey=mysubkey");

        assertEquals("xxx", endpoint.getConfiguration().getChannel());
        assertEquals("mysubkey", endpoint.getConfiguration().getSubscribeKey());
        assertTrue(endpoint.getConfiguration().isSecure());
    }

    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        PubNubComponent component = context.getComponent("pubnub", PubNubComponent.class);
        PubNubEndpoint endpoint = (PubNubEndpoint) component
                .createEndpoint(
                        "pubnub:xxx?subscribeKey=mysubkey&publishKey=mypubkey&secretKey=secrets&uuid=myuuid&operation=PUBLISH&secure=false&authKey=authKey");

        assertEquals("xxx", endpoint.getConfiguration().getChannel());
        assertEquals("mysubkey", endpoint.getConfiguration().getSubscribeKey());
        assertEquals("mypubkey", endpoint.getConfiguration().getPublishKey());
        assertEquals("secrets", endpoint.getConfiguration().getSecretKey());
        assertEquals("myuuid", endpoint.getConfiguration().getUuid());
        assertEquals("PUBLISH", endpoint.getConfiguration().getOperation());
        assertEquals("authKey", endpoint.getConfiguration().getAuthKey());
        assertFalse(endpoint.getConfiguration().isSecure());
    }

}
