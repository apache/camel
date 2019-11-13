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
package org.apache.camel.component.infinispan;

import java.io.IOException;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.MarshallerUtil;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.commons.util.Util;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.sampledomain.User;
import org.infinispan.protostream.sampledomain.marshallers.GenderMarshaller;
import org.infinispan.protostream.sampledomain.marshallers.UserMarshaller;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.infinispan.query.remote.client.impl.MarshallerRegistration;
import org.junit.Test;

import static org.apache.camel.component.infinispan.util.UserUtils.CQ_USERS;
import static org.apache.camel.component.infinispan.util.UserUtils.createKey;

public class InfinispanContinuousQueryIT extends CamelTestSupport {

    @BindToRegistry("continuousQueryBuilder")
    private static final InfinispanQueryBuilder CONTINUOUS_QUERY_BUILDER = new InfinispanQueryBuilder() {
        @Override
        public Query build(QueryFactory queryFactory) {
            return queryFactory.from(User.class)
                .having("name").like("CQ%").build();
        }
    };

    @BindToRegistry("continuousQueryBuilderNoMatch")
    private static final InfinispanQueryBuilder CONTINUOUS_QUERY_BUILDER_NO_MATCH = new InfinispanQueryBuilder() {
        @Override
        public Query build(QueryFactory queryFactory) {
            return queryFactory.from(User.class)
                .having("name").like("%TEST%").build();
        }
    };

    @BindToRegistry("continuousQueryBuilderAll")
    private static final InfinispanQueryBuilder CONTINUOUS_QUERY_BUILDER_ALL = new InfinispanQueryBuilder() {
        @Override
        public Query build(QueryFactory queryFactory) {
            return queryFactory.from(User.class)
                .having("name").like("%Q0%").build();
        }
    };

    @BindToRegistry("myCustomContainer")
    private RemoteCacheManager manager;
    
    @BindToRegistry("continuousQueryBuilder")
    private RemoteCache<Object, Object> cache;

    @Override
    protected void doPreSetup() throws IOException {
        ConfigurationBuilder builder = new ConfigurationBuilder()
            .addServer()
            .host("localhost")
            .port(11222)
            .marshaller(new ProtoStreamMarshaller());

        manager = new RemoteCacheManager(builder.build());

        RemoteCache<String, String> metadataCache = manager.getCache(
            ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
        metadataCache.put(
            "sample_bank_account/bank.proto",
            Util.read(InfinispanContinuousQueryIT.class.getResourceAsStream("/sample_bank_account/bank.proto")));

        MarshallerRegistration.init(MarshallerUtil.getSerializationContext(manager));

        SerializationContext serCtx = MarshallerUtil.getSerializationContext(manager);
        serCtx.registerProtoFiles(FileDescriptorSource.fromResources("/sample_bank_account/bank.proto"));
        serCtx.registerMarshaller(new UserMarshaller());
        serCtx.registerMarshaller(new GenderMarshaller());

        // pre-load data
        cache = manager.administration().getOrCreateCache("remote_query", (String) null);
        cache.clear();
    }

    @Test
    public void continuousQuery() throws Exception {
        MockEndpoint continuousQueryBuilderNoMatch = getMockEndpoint("mock:continuousQueryNoMatch");
        continuousQueryBuilderNoMatch.expectedMessageCount(0);

        MockEndpoint continuousQueryBuilderAll = getMockEndpoint("mock:continuousQueryAll");
        continuousQueryBuilderAll.expectedMessageCount(CQ_USERS.length * 2);

        MockEndpoint continuousQuery = getMockEndpoint("mock:continuousQuery");
        continuousQuery.expectedMessageCount(4);

        for (int i = 0; i < 4; i++) {
            continuousQuery.message(i).header(InfinispanConstants.KEY).isEqualTo(createKey(CQ_USERS[i % 2]));
            continuousQuery.message(i).header(InfinispanConstants.CACHE_NAME).isEqualTo(cache.getName());
            if (i >= 2) {
                continuousQuery.message(i).header(InfinispanConstants.EVENT_TYPE).isEqualTo(InfinispanConstants.CACHE_ENTRY_LEAVING);
                continuousQuery.message(i).header(InfinispanConstants.EVENT_DATA).isNull();
            } else {
                continuousQuery.message(i).header(InfinispanConstants.EVENT_TYPE).isEqualTo(InfinispanConstants.CACHE_ENTRY_JOINING);
                continuousQuery.message(i).header(InfinispanConstants.EVENT_DATA).isNotNull();
                continuousQuery.message(i).header(InfinispanConstants.EVENT_DATA).isInstanceOf(User.class);
            }
        }

        for (final User user : CQ_USERS) {
            cache.put(createKey(user), user);
        }

        assertEquals(CQ_USERS.length, cache.size());

        for (final User user : CQ_USERS) {
            cache.remove(createKey(user));
        }

        assertTrue(cache.isEmpty());

        continuousQuery.assertIsSatisfied();
        continuousQueryBuilderNoMatch.assertIsSatisfied();
        continuousQueryBuilderAll.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("infinispan:remote_query?cacheContainer=#myCustomContainer&queryBuilder=#continuousQueryBuilder")
                    .to("mock:continuousQuery");
                from("infinispan:remote_query?cacheContainer=#myCustomContainer&queryBuilder=#continuousQueryBuilderNoMatch")
                    .to("mock:continuousQueryNoMatch");
                from("infinispan:remote_query?cacheContainer=#myCustomContainer&queryBuilder=#continuousQueryBuilderAll")
                    .to("mock:continuousQueryAll");
            }
        };
    }
}
