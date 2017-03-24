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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.json.JSONObject;

//@formatter:off
/**
 * Just a small http server hack to try out pubnub method calls. 
 * HERE_NOW, WHERE_NOW, GET_STATE, SET_STATE, GET_HISTORY, PUBLISH; 
 * usage : 
 * do a GET with http param CamelPubNubOperation=METHOD_TO_ACTIVATE eg. HERE_NOW 
 * 
 * SET_STATE requires a http param 'body' with some json that is used as pubnub state object. 
 * Can be any valid json string.
 *
 */
//@formatter:on
public final class PubNubOperationsExample {

    private PubNubOperationsExample() {
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new RestRoute());
        main.run();
    }

    static class RestRoute extends RouteBuilder {
        private String pubnub = "pubnub://pubsub:iot?publisherKey=" + PubNubExampleConstants.PUBNUB_PUBLISHER_KEY + "&subscriberKey="
                                + PubNubExampleConstants.PUBNUB_SUBSCRIBER_KEY;

        @Override
        public void configure() throws Exception {
            //@formatter:off
            from("netty-http:http://0.0.0.0:8080?urlDecodeHeaders=true")
                .setBody(simple("${header.body}"))
                .convertBodyTo(JSONObject.class)
                .to(pubnub);
            //@formatter:on
        }
    }

}
