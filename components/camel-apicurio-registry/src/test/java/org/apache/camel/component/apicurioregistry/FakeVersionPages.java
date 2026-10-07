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
package org.apache.camel.component.apicurioregistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import io.apicurio.registry.rest.client.groups.item.artifacts.item.versions.VersionsRequestBuilder;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionSortBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Stubs a mocked versions endpoint so that it behaves like the registry: it honours the orderby, order, offset and
 * limit query parameters and applies a default page size of 20 when no limit is given.
 */
final class FakeVersionPages {

    static final int REGISTRY_DEFAULT_LIMIT = 20;

    final List<SearchedVersion> versions = new ArrayList<>();
    final List<Integer> requestedOffsets = new ArrayList<>();
    final List<Integer> requestedLimits = new ArrayList<>();

    @SuppressWarnings("unchecked")
    FakeVersionPages(VersionsRequestBuilder builder) {
        when(builder.get()).thenAnswer(call -> page(builder, null));
        when(builder.get(any(Consumer.class))).thenAnswer(
                call -> page(builder, (Consumer<VersionsRequestBuilder.GetRequestConfiguration>) call.getArgument(0)));
    }

    static SearchedVersion version(long globalId) {
        SearchedVersion version = new SearchedVersion();
        version.setGlobalId(globalId);
        version.setVersion(String.valueOf(globalId));
        version.setArtifactType("JSON");
        return version;
    }

    FakeVersionPages add(long... globalIds) {
        for (long globalId : globalIds) {
            versions.add(version(globalId));
        }
        return this;
    }

    FakeVersionPages add(SearchedVersion version) {
        versions.add(version);
        return this;
    }

    FakeVersionPages addRange(long fromInclusive, long toInclusive) {
        for (long id = fromInclusive; id <= toInclusive; id++) {
            versions.add(version(id));
        }
        return this;
    }

    private VersionSearchResults page(
            VersionsRequestBuilder builder, Consumer<VersionsRequestBuilder.GetRequestConfiguration> configurer) {
        VersionsRequestBuilder.GetRequestConfiguration config = builder.new GetRequestConfiguration();
        if (configurer != null) {
            configurer.accept(config);
        }
        List<SearchedVersion> sorted = new ArrayList<>(versions);
        if (config.queryParameters.orderby == VersionSortBy.GlobalId) {
            Comparator<SearchedVersion> byGlobalId = Comparator.comparingLong(SearchedVersion::getGlobalId);
            sorted.sort(config.queryParameters.order == SortOrder.Desc ? byGlobalId.reversed() : byGlobalId);
        }
        int offset = config.queryParameters.offset != null ? config.queryParameters.offset : 0;
        int limit = config.queryParameters.limit != null ? config.queryParameters.limit : REGISTRY_DEFAULT_LIMIT;
        requestedOffsets.add(offset);
        requestedLimits.add(limit);
        VersionSearchResults results = new VersionSearchResults();
        results.setCount(sorted.size());
        results.setVersions(sorted.subList(Math.min(offset, sorted.size()), Math.min(offset + limit, sorted.size())));
        return results;
    }
}
