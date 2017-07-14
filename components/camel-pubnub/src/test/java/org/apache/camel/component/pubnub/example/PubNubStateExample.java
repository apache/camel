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
package org.apache.camel.component.pubnub.example;

import java.util.HashMap;
import java.util.Map;

import com.pubnub.api.models.consumer.presence.PNGetStateResult;
import com.pubnub.api.models.consumer.presence.PNSetStateResult;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pubnub.PubNubConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.camel.component.pubnub.example.PubNubExampleConstants.PUBNUB_PUBLISH_KEY;
import static org.apache.camel.component.pubnub.example.PubNubExampleConstants.PUBNUB_SUBSCRIBE_KEY;

@Ignore("Integration test that requires a pub/sub key to run")
public class PubNubStateExample extends CamelTestSupport {

    @Test
    public void testStateChange() throws Exception {
        Map<String, Object> myState = new HashMap<>();
        myState.put("state", "online");
        myState.put("name", "preben");
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(PubNubConstants.OPERATION, "SETSTATE");
        PNSetStateResult response = template.requestBodyAndHeaders("direct:publish", myState, headers, PNSetStateResult.class);
        assertNotNull(response);
        assertNotNull(response.getState());
        assertEquals("preben", response.getState().getAsJsonObject().get("name").getAsString());

        resetMocks();
        getMockEndpoint("mock:result").expectedMessageCount(1);
        headers.clear();
        headers.put(PubNubConstants.OPERATION, "GETSTATE");
        PNGetStateResult getStateResult = template.requestBodyAndHeader("direct:publish", null, PubNubConstants.OPERATION, "GETSTATE", PNGetStateResult.class);
        assertMockEndpointsSatisfied();
        assertEquals("preben", getStateResult.getStateByUUID().get("iot").getAsJsonObject().get("name").getAsString());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:publish").to("pubnub:iot?uuid=myuuid&publishKey=" + PUBNUB_PUBLISH_KEY + "&subscribeKey=" + PUBNUB_SUBSCRIBE_KEY)
                    .to("mock:result");
            }
        };
    }

}
