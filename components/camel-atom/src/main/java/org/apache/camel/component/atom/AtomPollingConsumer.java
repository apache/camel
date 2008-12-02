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
package org.apache.camel.component.atom;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.feed.FeedConsumer;

/**
 * Consumer to poll atom feeds and return the full feed.
 *
 * @version $Revision$
 */
public class AtomPollingConsumer extends FeedConsumer {

    public AtomPollingConsumer(AtomEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    protected void poll() throws Exception {
        Document<Feed> document = AtomUtils.parseDocument(endpoint.getFeedUri());
        Feed feed = document.getRoot();
        Exchange exchange = endpoint.createExchange(feed);
        getProcessor().process(exchange);
    }

}
