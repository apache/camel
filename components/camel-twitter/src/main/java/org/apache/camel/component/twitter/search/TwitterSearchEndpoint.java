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
package org.apache.camel.component.twitter.search;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.AbstractTwitterEndpoint;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.component.twitter.TwitterHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * The Twitter Search component consumes search results.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "twitter-search", title = "Twitter Search", syntax = "twitter-search:keywords",
    consumerClass = SearchConsumerHandler.class, label = "api,social")
public class TwitterSearchEndpoint extends AbstractTwitterEndpoint {

    @UriPath(description = "The search keywords. Multiple values can be separated with comma.")
    @Metadata(required = "true")
    private String keywords;

    public TwitterSearchEndpoint(String uri, String remaining, TwitterSearchComponent component, TwitterConfiguration properties) {
        super(uri, component, properties);
        this.keywords = remaining;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SearchProducer(this, keywords);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return TwitterHelper.createConsumer(processor, this, new SearchConsumerHandler(this, keywords));
    }

    public String getKeywords() {
        return keywords;
    }
}
