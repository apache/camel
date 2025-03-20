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
package org.apache.camel.component.infinispan.remote.embeddingstore;

import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;

public class InfinispanVectorQueryBuilder implements InfinispanQueryBuilder {
    private static final String QUERY_TEMPLATE = "select i, score(i) from %s i where i.embedding <-> [:vector]~:distance";
    private final float[] vector;
    private int distance;
    private String typeName;

    public InfinispanVectorQueryBuilder(float[] vector) {
        this.vector = vector;
    }

    @Override
    public Query<?> build(QueryFactory queryFactory) {
        Query<?> query = InfinispanQueryBuilder.create(QUERY_TEMPLATE.formatted(typeName)).build(queryFactory);
        query.setParameter("vector", vector);
        query.setParameter("distance", distance);
        return query;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }
}
