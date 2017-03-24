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
package org.apache.camel.component.pubnub;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PubNubOperationsTest extends CamelTestSupport {

    @Test
    public void testWhereNow() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(PubNubConstants.OPERATION, "WHERE_NOW");
        headers.put(PubNubConstants.UUID, "uuid");
        JSONObject response = template.requestBodyAndHeaders("direct:publish", null, headers, JSONObject.class);
        assertNotNull(response);
        assertEquals("hello_world", response.getJSONArray("channels").getString(0));
    }

    @Test
    public void testHereNow() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(PubNubConstants.OPERATION, "HERE_NOW");
        JSONObject response = template.requestBodyAndHeaders("direct:publish", null, headers, JSONObject.class);
        assertNotNull(response);
        assertEquals(3, response.getInt("occupancy"));
    }

    @Test
    public void testGetHistory() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(PubNubConstants.OPERATION, "GET_HISTORY");
        JSONArray response = template.requestBodyAndHeaders("direct:publish", null, headers, JSONArray.class);
        assertNotNull(response);
        assertEquals("message1", response.getJSONArray(0).getString(0));
    }

    @Test
    public void testSetAndGetState() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(PubNubConstants.OPERATION, "SET_STATE");
        headers.put(PubNubConstants.UUID, "myuuid");
        JSONObject state = new JSONObject("{\"state\":\"active\", \"lat\":\"55.645499\", \"lon\":\"12.370967\"}");
        template.sendBodyAndHeaders("direct:publish", state, headers);
        headers.replace(PubNubConstants.OPERATION, "GET_STATE");
        JSONObject response = template.requestBodyAndHeaders("direct:publish", null, headers, JSONObject.class);
        assertNotNull(response);
        assertEquals(state, response);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("pubnub", new PubNubMock("dummy", "dummy"));
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                //@formatter:off
                from("direct:publish").to("pubnub://pubsub:mychannel?uuid=myuuid&pubnub=#pubnub")
                .to("log:io.rhiot.component.pubnub?showAll=true&multiline=true")
                .to("mock:result");
                //@formatter:on
            }
        };
    }
}
