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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pubnub.api.models.consumer.history.PNHistoryItemResult;
import com.pubnub.api.models.consumer.presence.PNGetStateResult;
import com.pubnub.api.models.consumer.presence.PNHereNowResult;
import com.pubnub.api.models.consumer.presence.PNSetStateResult;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public class PubNubOperationsTest extends PubNubTestBase {

    @Test
    public void testWhereNow() throws Exception {
        stubFor(get(urlPathEqualTo("/v2/presence/sub-key/mySubscribeKey/uuid/myUUID"))
            .willReturn(aResponse().withBody("{\"status\": 200, \"message\": \"OK\", \"payload\": {\"channels\": [\"channel-a\",\"channel-b\"]}, \"service\": \"Presence\"}")));

        Map<String, Object> headers = new HashMap<>();
        headers.put(PubNubConstants.OPERATION, "WHERENOW");
        headers.put(PubNubConstants.UUID, "myUUID");
        @SuppressWarnings("unchecked")
        List<String> response = template.requestBodyAndHeaders("direct:publish", null, headers, List.class);
        assertNotNull(response);
        assertListSize(response, 2);
        assertEquals("channel-a", response.get(0));
    }

    @Test
    public void testHereNow() throws Exception {
        stubFor(get(urlPathEqualTo("/v2/presence/sub_key/mySubscribeKey/channel/myChannel")).willReturn(aResponse()
            .withBody("{\"status\" : 200, \"message\" : \"OK\", \"service\" : \"Presence\", \"uuids\" : [{\"uuid\" : \"myUUID0\"}, {\"state\" : {\"abcd\" : {\"age\" : 15}}, "
                      + "\"uuid\" : \"myUUID1\"}, {\"uuid\" : \"b9eb408c-bcec-4d34-b4c4-fabec057ad0d\"}, {\"state\" : {\"abcd\" : {\"age\" : 15}}, \"uuid\" : \"myUUID2\"},"
                      + " {\"state\" : {\"abcd\" : {\"age\" : 24}}, \"uuid\" : \"myUUID9\"}], \"occupancy\" : 5}")));
        Map<String, Object> headers = new HashMap<>();
        headers.put(PubNubConstants.OPERATION, "HERENOW");
        PNHereNowResult response = template.requestBodyAndHeaders("direct:publish", null, headers, PNHereNowResult.class);
        assertNotNull(response);
        assertEquals(5, response.getTotalOccupancy());
    }

    @Test
    public void testGetHistory() throws Exception {
        List<Object> testArray = new ArrayList<>();
        List<Object> historyItems = new ArrayList<>();

        Map<String, Object> historyEnvelope1 = new HashMap<>();
        Map<String, Object> historyItem1 = new HashMap<>();
        historyItem1.put("a", 11);
        historyItem1.put("b", 22);
        historyEnvelope1.put("timetoken", 1111);
        historyEnvelope1.put("message", historyItem1);

        Map<String, Object> historyEnvelope2 = new HashMap<>();
        Map<String, Object> historyItem2 = new HashMap<>();
        historyItem2.put("a", 33);
        historyItem2.put("b", 44);
        historyEnvelope2.put("timetoken", 2222);
        historyEnvelope2.put("message", historyItem2);

        historyItems.add(historyEnvelope1);
        historyItems.add(historyEnvelope2);

        testArray.add(historyItems);
        testArray.add(1234);
        testArray.add(4321);

        stubFor(get(urlPathEqualTo("/v2/history/sub-key/mySubscribeKey/channel/myChannel")).willReturn(aResponse().withBody(getPubnub().getMapper().toJson(testArray))));

        Map<String, Object> headers = new HashMap<>();
        headers.put(PubNubConstants.OPERATION, "GETHISTORY");
        @SuppressWarnings("unchecked")
        List<PNHistoryItemResult> response = template.requestBodyAndHeaders("direct:publish", null, headers, List.class);
        assertNotNull(response);
        assertListSize(response, 2);
    }

    @Test
    public void testGetState() throws Exception {
        stubFor(get(urlPathEqualTo("/v2/presence/sub-key/mySubscribeKey/channel/myChannel/uuid/myuuid")).willReturn(aResponse()
            .withBody("{ \"status\": 200, \"message\": \"OK\", \"payload\": "
                      + "{ \"myChannel\": { \"age\" : 20, \"status\" : \"online\"}, \"ch2\": { \"age\": 100, \"status\": \"offline\" } }, \"service\": \"Presence\"}")));
        Map<String, Object> headers = new HashMap<>();
        headers.put(PubNubConstants.OPERATION, "GETSTATE");
        PNGetStateResult response = template.requestBodyAndHeaders("direct:publish", null, headers, PNGetStateResult.class);
        assertNotNull(response);
        assertNotNull(response.getStateByUUID().get("myChannel"));
    }

    @Test
    public void testSetState() throws Exception {
        stubFor(get(urlPathEqualTo("/v2/presence/sub-key/mySubscribeKey/channel/myChannel/uuid/myuuid/data"))
                .willReturn(aResponse().withBody("{ \"status\": 200, \"message\": \"OK\", \"payload\": { \"age\" : 20, \"status\" : \"online\" }, \"service\": \"Presence\"}")));
        Map<String, Object> myState = new HashMap<>();
        myState.put("age", 20);
        Map<String, Object> headers = new HashMap<>();
        headers.put(PubNubConstants.OPERATION, "SETSTATE");
        PNSetStateResult response = template.requestBodyAndHeaders("direct:publish", myState, headers, PNSetStateResult.class);
        assertNotNull(response);
        assertNotNull(response.getState());
        assertEquals(20, response.getState().getAsJsonObject().get("age").getAsInt());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:publish").to("pubnub://myChannel?uuid=myuuid&pubnub=#pubnub")
                    .to("mock:result");
            }
        };
    }
}
