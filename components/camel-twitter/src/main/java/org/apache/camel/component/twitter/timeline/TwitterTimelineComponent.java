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
package org.apache.camel.component.twitter.timeline;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.twitter.AbstractTwitterComponent;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;

/**
 * Twitter Timeline component.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
@Component("twitter-timeline")
public class TwitterTimelineComponent extends AbstractTwitterComponent {

    public TwitterTimelineComponent() {
        super("twitter-timeline");
    }

    public TwitterTimelineComponent(CamelContext context) {
        super(context, "twitter-timeline");
    }

    @Override
    protected Endpoint doCreateEndpoint(TwitterConfiguration properties, String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String user = getAndRemoveParameter(parameters, "user", String.class);
        return new TwitterTimelineEndpoint(uri, remaining, user, this, properties);
    }
}
