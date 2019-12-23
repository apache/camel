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
package org.apache.camel.component.olingo2;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.olingo.odata2.api.ep.entry.EntryMetadata;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.ep.feed.FeedMetadata;
import org.apache.olingo.odata2.api.ep.feed.ODataFeed;

public class Olingo2Index {

    private Set<Integer> resultIndex = new HashSet<>();

    private class EmptyODataFeed implements ODataFeed {

        @Override
        public List<ODataEntry> getEntries() {
            return Collections.emptyList();
        }

        @Override
        public FeedMetadata getFeedMetadata() {
            return new FeedMetadata() {
                @Override
                public String getDeltaLink() {
                    return null;
                }

                @Override
                public Integer getInlineCount() {
                    return 0;
                }

                @Override
                public String getNextLink() {
                    return null;
                }
            };
        }
    }

    /**
     * Hash only certain data since other parts change between message
     * exchanges.
     *
     * @param metadata
     * @return hashcode of metadata
     */
    private int hash(EntryMetadata metadata) {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((metadata.getId() == null) ? 0 : metadata.getId().hashCode());
        result = prime * result + ((metadata.getUri() == null) ? 0 : metadata.getUri().hashCode());
        return result;
    }

    /**
     * Hash entry leaving out certain fields that change between exchange
     * messages
     *
     * @param entry
     * @return hascode of entry
     */
    private int hash(ODataEntry entry) {
        final int prime = 31;
        int result = 1;
        // Hash metadata to ignore certain entries
        result = prime * result + ((entry.getMetadata() == null) ? 0 : hash(entry.getMetadata()));
        result = prime * result + ((entry.getProperties() == null) ? 0 : entry.getProperties().hashCode());

        // Ignore mediaMetadata, expandSelectTree since its object changes each
        // time

        return result;
    }

    private Object filter(Object o) {
        if (o == null || resultIndex.contains(o.hashCode())) {
            return null;
        }
        return o;
    }

    private void indexDefault(Object o) {
        if (o == null) {
            return;
        }

        resultIndex.add(o.hashCode());
    }

    private Object filter(ODataEntry entry) {
        if (entry == null || resultIndex.contains(hash(entry))) {
            return null;
        }
        return entry;
    }

    private void index(ODataEntry entry) {
        if (entry == null) {
            return;
        }

        resultIndex.add(hash(entry));
    }

    private Iterable<?> filter(Iterable<?> iterable) {
        List<Object> filtered = new ArrayList<>();
        if (iterable == null) {
            return filtered;
        }

        for (Object o : iterable) {
            if (resultIndex.contains(o.hashCode())) {
                continue;
            }
            filtered.add(o);
        }

        return filtered;
    }

    private void index(Iterable<?> iterable) {
        if (iterable == null) {
            return;
        }

        for (Object o : iterable) {
            resultIndex.add(o.hashCode());
        }
    }

    private ODataFeed filter(ODataFeed odataFeed) {
        if (odataFeed == null) {
            return new EmptyODataFeed();
        }

        List<ODataEntry> entries = odataFeed.getEntries();

        if (entries.isEmpty()) {
            return odataFeed;
        }

        List<ODataEntry> copyEntries = new ArrayList<>();
        copyEntries.addAll(entries);

        for (ODataEntry entry : copyEntries) {
            if (resultIndex.contains(hash(entry))) {
                entries.remove(entry);
            }
        }
        return odataFeed;
    }

    private void index(ODataFeed odataFeed) {
        if (odataFeed == null) {
            return;
        }

        for (ODataEntry entry : odataFeed.getEntries()) {
            resultIndex.add(hash(entry));
        }
    }

    /**
     * Index the results
     */
    public void index(Object result) {
        if (result instanceof ODataFeed) {
            index((ODataFeed)result);
        } else if (result instanceof Iterable) {
            index((Iterable<?>)result);
        } else if (result instanceof ODataEntry) {
            index((ODataEntry)result);
        } else {
            indexDefault(result);
        }
    }

    @SuppressWarnings("unchecked")
    public Object filterResponse(Object response) {
        if (response instanceof ODataFeed) {
            response = filter((ODataFeed)response);
        } else if (response instanceof Iterable) {
            response = filter((Iterable<Object>)response);
        } else if (response.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            final int size = Array.getLength(response);
            for (int i = 0; i < size; i++) {
                result.add(Array.get(response, i));
            }
            response = filter(result);
        } else if (response instanceof ODataEntry) {
            response = filter((ODataEntry)response);
        } else {
            response = filter(response);
        }

        return response;
    }
}
