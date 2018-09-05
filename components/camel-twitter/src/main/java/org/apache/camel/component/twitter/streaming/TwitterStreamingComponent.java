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
package org.apache.camel.component.twitter.streaming;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.twitter.AbstractTwitterComponent;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.spi.Metadata;

/**
 * Twitter Streaming component.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class TwitterStreamingComponent extends AbstractTwitterComponent {

    public TwitterStreamingComponent() {
        super("twitter-streaming");
    }

    public TwitterStreamingComponent(CamelContext context) {
        super(context, "twitter-streaming");
    }

    @Override
    protected Endpoint doCreateEndpoint(TwitterConfiguration properties, String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String keywords = getAndRemoveParameter(parameters, "keywords", String.class);
        return new TwitterStreamingEndpoint(uri, remaining, keywords, this, properties);
    }
}
