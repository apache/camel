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
package org.apache.camel.component.azure.cosmosdb;

import com.azure.core.util.IterableStream;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedFlux;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Flux;

public final class CosmosDbUtils {

    private CosmosDbUtils() {
    }

    public static <
            T> Flux<T> convertCosmosPagedFluxToFluxResults(final CosmosPagedFlux<T> pagedFlux, final Integer maxResults) {
        final Flux<FeedResponse<T>> byPageFlux;

        if (ObjectHelper.isEmpty(maxResults)) {
            byPageFlux = pagedFlux.byPage();
        } else {
            byPageFlux = pagedFlux.byPage(maxResults);
        }

        return byPageFlux.flatMap(tFeedResponse -> {
            IterableStream<T> elements = tFeedResponse.getElements();
            if (elements == null) {
                return Flux.empty();
            }
            return Flux.fromIterable(elements);
        });
    }

    public static <T> Flux<T> convertCosmosPagedFluxToFluxResults(final CosmosPagedFlux<T> pagedFlux) {
        return convertCosmosPagedFluxToFluxResults(pagedFlux, null);
    }

    public static void validateIfParameterIsNotEmpty(final Object param, final String paramName) {
        if (ObjectHelper.isEmpty(param)) {
            throw new IllegalArgumentException(paramName + " cannot be empty!");
        }
    }
}
