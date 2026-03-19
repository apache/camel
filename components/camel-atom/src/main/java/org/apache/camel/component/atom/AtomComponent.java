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
package org.apache.camel.component.atom;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.feed.FeedComponent;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.URISupport;

/**
 * To consume Atom RSS feeds.
 */
@Component("atom")
public class AtomComponent extends FeedComponent {

    private static final String KEY_FORMAT_STRATEGY_PARAM = "idempotentStrategy";

    public AtomComponent() {
    }

    @Override
    protected FeedEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String idempotentStrategy = (String) parameters.get(KEY_FORMAT_STRATEGY_PARAM);
        AtomIdempotentStrategy atomIdempotentStrategy = resolveEnumIdempotentStrategy(idempotentStrategy);
        if (atomIdempotentStrategy != null) {
            parameters.put(KEY_FORMAT_STRATEGY_PARAM, atomIdempotentStrategy);
        }
        return new AtomEndpoint(uri, this, null);
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters)
            throws Exception {
        AtomEndpoint atom = (AtomEndpoint) endpoint;
        if (atom.getFeedUri() != null) {
            // already set so do not change it
            return;
        }

        // recreate feed uri after we have configured the endpoint so we can use the left over parameters
        // for the http feed
        String feedUri;
        if (!parameters.isEmpty()) {
            URI remainingUri = URISupport.createRemainingURI(new URI(remaining), parameters);
            feedUri = remainingUri.toString();
        } else {
            feedUri = remaining;
        }

        atom.setFeedUri(feedUri);
    }

    /**
     * Resolves the standard supported {@link AtomIdempotentStrategy} by a name which can be:
     * <ul>
     * <li>default - to use the default date strategy</li>
     * <li>repository - to use the Guid Repository strategy</li>
     * </ul>
     *
     * @param  name the name
     * @return      the strategy, or <tt>null</tt> if not a standard name.
     */
    private static AtomIdempotentStrategy resolveEnumIdempotentStrategy(String name) {
        if ("default".equalsIgnoreCase(name)) {
            return new ItemUpdatedIdempotentStrategy();
        } else if ("repository".equalsIgnoreCase(name)) {
            return new RepositoryGuidIdempotentStrategy();
        } else {
            return null;
        }
    }
}
