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

import java.io.IOException;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.camel.Processor;
import org.apache.camel.component.feed.FeedPollingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer to poll atom feeds and return the full feed.
 *
 * @version 
 */
public class AtomPollingConsumer extends FeedPollingConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(AtomPollingConsumer.class);

    public AtomPollingConsumer(AtomEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected Object createFeed() throws IOException {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            if (endpoint.getCamelContext().getApplicationContextClassLoader() != null) {
                Thread.currentThread().setContextClassLoader(endpoint.getCamelContext().getApplicationContextClassLoader());
                LOG.debug("set the TCCL to be " + endpoint.getCamelContext().getApplicationContextClassLoader());
            }
            Document<Feed> document = AtomUtils.parseDocument(endpoint.getFeedUri());
            return document.getRoot();
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
