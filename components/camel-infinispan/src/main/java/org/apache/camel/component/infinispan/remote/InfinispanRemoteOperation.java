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
package org.apache.camel.component.infinispan.remote;

import org.apache.camel.Message;
import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.Search;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.query.dsl.Query;

public final class InfinispanRemoteOperation {
    private InfinispanRemoteOperation() {
    }

    public static Query buildQuery(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
        InfinispanQueryBuilder queryBuilder = message.getHeader(InfinispanConstants.QUERY_BUILDER, InfinispanQueryBuilder.class);
        if (queryBuilder == null) {
            queryBuilder = configuration.getQueryBuilder();
        }

        return buildQuery(queryBuilder, cache);
    }

    public static Query buildQuery(InfinispanConfiguration configuration, BasicCache<Object, Object> cache) {
        return buildQuery(configuration.getQueryBuilder(), cache);
    }

    public static Query buildQuery(InfinispanQueryBuilder queryBuilder, BasicCache<Object, Object> cache) {
        return buildQuery(queryBuilder, InfinispanUtil.asRemote(cache));
    }

    public static Query buildQuery(InfinispanQueryBuilder queryBuilder, RemoteCache<Object, Object> cache) {
        return queryBuilder != null ? queryBuilder.build(Search.getQueryFactory(cache)) : null;
    }
}
