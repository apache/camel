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
package org.apache.camel.component.infinispan;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class InfinispanLocalConsumerCustomListenerIT extends InfinispanTestSupport {
    private static final String CUSTOM_CACHE_NAME = "customCacheName";
    
    @BindToRegistry("myCustomListener")
    MyEmbeddedCustomListener c = new MyEmbeddedCustomListener(CUSTOM_CACHE_NAME);

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("infinispan:%s?cacheContainer=#cacheContainer&customListener=#myCustomListener", CUSTOM_CACHE_NAME)
                    .to("mock:result");
            }
        };
    }

    @Test
    public void createEventConsumed() throws InterruptedException {
        //One for the existing entry and one for the new entry.
        mockResult.expectedMessageCount(2);

        basicCacheContainer.getCache(CUSTOM_CACHE_NAME).put("newKey", "newValue");

        mockResult.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(true);
        mockResult.message(0).header(InfinispanConstants.KEY).isEqualTo("newKey");
        mockResult.message(1).header(InfinispanConstants.IS_PRE).isEqualTo(false);

        mockResult.assertIsSatisfied();
    }
}
