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

import java.net.URI;
import java.util.Map;

import org.apache.camel.component.feed.FeedComponent;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.util.URISupport;

/**
 * An <a href="http://camel.apache.org/rss.html">RSS Component</a>.
 * <p/>
 * Camel uses <a href="https://rome.dev.java.net/">ROME</a> as the RSS implementation.  
 */
public class RssComponent extends FeedComponent {

    protected FeedEndpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {

        // Parameters should be kept in the remaining path, since they might be needed to get the actual RSS feed
        URI remainingUri = URISupport.createRemainingURI(new URI(remaining), parameters);

        if (remainingUri.getScheme().equals("http") || remainingUri.getScheme().equals("https")) {
            return new RssEndpoint(uri, this, remainingUri.toString());
        }

        return new RssEndpoint(uri, this, remaining);
    }
    
}