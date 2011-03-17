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
package org.apache.camel.component.rss;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;

public class RssEndpointTest extends RssPollingConsumerTest {

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                RssEndpoint rss = new RssEndpoint();
                rss.setCamelContext(context);
                rss.setFeedUri("file:src/test/data/rss20.xml");
                rss.setSplitEntries(false);

                Map<String, Object> map = new HashMap<String, Object>();
                map.put("delay", 100);
                rss.setConsumerProperties(map);

                context.addEndpoint("myrss", rss);

                from("myrss").to("mock:result");
            }
        };
    }

}