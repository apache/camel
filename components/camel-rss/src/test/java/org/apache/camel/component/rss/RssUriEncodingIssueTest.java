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

import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version
 */
@Ignore("Must be online")
public class RssUriEncodingIssueTest extends CamelTestSupport {

    @Test
    public void testUriIssue() throws Exception {
        String uri = "rss:http://api.flickr.com/services/feeds/photos_public.gne?id=23353282@N05&tags=lowlands&lang=en-us&format=rss_200";

        PollingConsumer consumer = context.getEndpoint(uri).createPollingConsumer();
        consumer.start();
        Exchange exchange = consumer.receive();
        log.info("Receive " + exchange);
        assertNotNull(exchange);
        assertNotNull(exchange.getIn().getBody());
        consumer.stop();
    }

}
