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
package org.apache.camel.component.solr;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.SimpleOrderedMap;

public class SolrUtils {

    public static Map<String, Object> parseAsMap(SolrResponse solrResponse) {
        return solrResponse == null || solrResponse.getResponse() == null
                ? Map.of()
                : solrResponse.getResponse().asShallowMap(true);
    }

    public static Map<String, Object> parseAsFlatMap(SolrResponse solrResponse) {
        return parseAsFlatMap(parseAsMap(solrResponse));
    }

    public static Map<String, Object> parseAsFlatMap(Map<String, Object> map1) {
        return parseAsFlatMap(map1, null, null);
    }

    public static Map<String, Object> parseAsFlatMap(Map<String, Object> map1, String startsWith, String endsWith) {
        return map1.entrySet().stream()
                .flatMap(SolrUtils::flatten)
                .filter(e -> e != null && e.getKey() != null && e.getValue() != null)
                .filter(e -> startsWith == null || e.getKey().startsWith(startsWith))
                .filter(e -> endsWith == null || e.getKey().endsWith(endsWith))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SuppressWarnings("unchecked")
    public static Stream<Map.Entry<String, Object>> flatten(Map.Entry<String, Object> entry) {
        Map<String, Object> nestedMap = null;
        if (entry.getValue() instanceof final SimpleOrderedMap<?> nested) {
            nestedMap = (Map<String, Object>) nested.asShallowMap(true);
        } else if (entry.getValue() instanceof final Map<?, ?> nested) {
            nestedMap = (Map<String, Object>) nested;
        }
        if (nestedMap != null) {
            return nestedMap.entrySet().stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(entry.getKey() + "." + e.getKey(), e.getValue()))
                    .flatMap(SolrUtils::flatten);
        }
        return Stream.of(entry);
    }

    public static void addHeadersForCommit(ModifiableSolrParams solrParams) {
        getHeadersForCommit("commit", null)
                .forEach((k, v) -> solrParams.add(k, String.valueOf(v)));
    }

    public static void addHeadersForCommit(Exchange exchange) {
        addHeadersForCommit(exchange, "commit");
    }

    public static void addHeadersForCommit(Exchange exchange, String commitParam) {
        exchange.getMessage().getHeaders().putAll(getHeadersForCommit(commitParam));
    }

    public static Map<String, Object> getHeadersForCommit() {
        return getHeadersForCommit("commit");
    }

    public static Map<String, Object> getHeadersForCommit(String commitParam) {
        return getHeadersForCommit(commitParam, SolrConstants.HEADER_PARAM_PREFIX);
    }

    public static Map<String, Object> getHeadersForCommit(String commitParam, String prefix) {
        String finalPrefix = prefix == null ? "" : prefix;
        return Map.of(finalPrefix + commitParam, "true");
    }

    public static boolean isCollectionOfType(Object body, Class<?> clazz) {
        return body instanceof Collection<?> collection && collection.stream().allMatch(clazz::isInstance);
    }

    public static <T> List<T> convertToList(Collection<T> collection) {
        return collection instanceof List
                ? (List<T>) collection
                : new ArrayList<>(collection);
    }

}
