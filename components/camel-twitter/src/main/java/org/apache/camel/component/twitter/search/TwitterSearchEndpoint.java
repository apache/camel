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
package org.apache.camel.component.twitter.search;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.AbstractTwitterEndpoint;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.component.twitter.TwitterConstants;
import org.apache.camel.component.twitter.TwitterHelper;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.twitter.TwitterConstants.SCHEME_SEARCH;

/**
 * Access Twitter Search.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = SCHEME_SEARCH, title = "Twitter Search", syntax = "twitter-search:keywords",
             category = { Category.SAAS, Category.SOCIAL },
             headersClass = TwitterConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "twitter4j.v1.Status",
                                   description = "One tweet found by the search per exchange, with its text in getText()"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not read: the search uses the keywords of the endpoint, or of"
                                                + " the CamelTwitterKeywords header"),
           producerOut = @BodyType(value = Carrier.LIST, javaType = "java.util.List<twitter4j.v1.Status>",
                                   description = "The tweets found, an empty List when there are none"))
public class TwitterSearchEndpoint extends AbstractTwitterEndpoint {

    @UriPath(description = "The search query, use the keywords AND, OR, - and () to narrow the search results.")
    @Metadata(required = true)
    private String keywords;

    public TwitterSearchEndpoint(String uri, String remaining, TwitterSearchComponent component,
                                 TwitterConfiguration properties) {
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
