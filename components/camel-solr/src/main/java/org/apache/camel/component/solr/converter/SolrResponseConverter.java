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
package org.apache.camel.component.solr.converter;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.solr.SolrUtils;
import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.SolrResponseBase;
import org.apache.solr.common.util.NamedList;

@Converter(generateLoader = true)
@SuppressWarnings("unchecked")
public final class SolrResponseConverter {

    private SolrResponseConverter() {
    }

    @Converter
    public static Map<String, Object> createSolrResponseMap(Object body, Exchange exchange) {
        return SolrUtils.parseAsFlatMap(SolrUtils.parseAsMap(createSolrResponse(body, exchange)));
    }

    @Converter
    public static SolrResponse createSolrResponse(Object body, Exchange exchange) {
        return createSolrResponse(body, SolrResponseBase.class);
    }

    @Converter
    public static SolrPingResponse createSolrPingResponse(Object body, Exchange exchange) {
        return createSolrResponse(body, SolrPingResponse.class);
    }

    @Converter
    public static QueryResponse createQueryResponse(Object body, Exchange exchange) {
        return createSolrResponse(body, QueryResponse.class);
    }

    private static <T extends SolrResponse> T createSolrResponse(Object body, Class<T> type) {
        if (body instanceof NamedList<?> namedList) {
            T t;
            try {
                t = type.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            t.setResponse((NamedList<Object>) namedList);
            return t;
        }
        return null;
    }

}
