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
package org.apache.camel.component.rss;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.feed.FeedComponent;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.URISupport;

/**
 * An <a href="http://camel.apache.org/rss.html">RSS Component</a>.
 * <p/>
 * Camel uses <a href="https://rome.dev.java.net/">ROME</a> as the RSS implementation.  
 */
@Component("rss")
public class RssComponent extends FeedComponent {

    public RssComponent() {
    }

    @Override
    protected FeedEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        return new RssEndpoint(uri, this, null);
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters) throws Exception {
        RssEndpoint rss = (RssEndpoint) endpoint;
        if (rss.getFeedUri() != null) {
            // already set so do not change it
            return;
        }

        // recreate feed uri after we have configured the endpoint so we can use the left over parameters
        // for the http feed
        String feedUri;
        if (!parameters.isEmpty()) {
            Map<String, Object> options = new LinkedHashMap<>(parameters);
            String query = URISupport.createQueryString(options);
            feedUri = remaining + "?" + query;
        } else {
            feedUri = remaining;
        }

        rss.setFeedUri(feedUri);
    }
}