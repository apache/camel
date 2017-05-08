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

import org.apache.camel.ComponentVerifier;
import org.apache.camel.Endpoint;
import org.apache.camel.component.twitter.AbstractTwitterComponent;
import org.apache.camel.component.twitter.DefaultTwitterComponentVerifier;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.spi.Metadata;

/**
 * Twitter Streaming component.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class TwitterStreamingComponent extends AbstractTwitterComponent {

    protected Endpoint doCreateEndpoint(TwitterConfiguration properties, String uri, String remaining, Map<String, Object> parameters) throws Exception {
        return new TwitterStreamingEndpoint(uri, remaining, this, properties);
    }

    /**
     * Get a verifier for the twitter streaming component.
     */
    public ComponentVerifier getVerifier() {
        return new DefaultTwitterComponentVerifier(this, "twitter-streaming");
    }
}
