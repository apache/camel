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
package org.apache.camel.component.infinispan;

import java.io.IOException;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.commons.util.Util;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.sampledomain.User;
import org.infinispan.protostream.sampledomain.marshallers.GenderMarshaller;
import org.infinispan.protostream.sampledomain.marshallers.UserMarshaller;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.remote.client.MarshallerRegistration;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.junit.Test;

import static org.apache.camel.component.infinispan.InfinispanConstants.OPERATION;
import static org.apache.camel.component.infinispan.InfinispanConstants.QUERY;
import static org.apache.camel.component.infinispan.InfinispanConstants.QUERY_BUILDER;
import static org.apache.camel.component.infinispan.util.UserUtils.USERS;
import static org.apache.camel.component.infinispan.util.UserUtils.createKey;
import static org.apache.camel.component.infinispan.util.UserUtils.hasUser;

public class InfinispanRemoteQueryProducerIT extends CamelTestSupport {

    private static final InfinispanQueryBuilder NO_RESULT_QUERY_BUILDER = new InfinispanQueryBuilder() {
        @Override
        public Query build(QueryFactory queryFactory) {
            return queryFactory.from(User.class)
                .having("name").like("%abc%")
                .toBuilder().build();
        }
    };

    private static final InfinispanQueryBuilder WITH_RESULT_QUERY_BUILDER = new InfinispanQueryBuilder() {
        @Override
        public Query build(QueryFactory queryFactory) {
            return queryFactory.from(User.class)
                .having("name").like("%A")
                .toBuilder().build();
        }
    };

    private RemoteCacheManager manager;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("myCustomContainer", manager);
        registry.bind("noResultQueryBuilder", NO_RESULT_QUERY_BUILDER);
        registry.bind("withResultQueryBuilder", WITH_RESULT_QUERY_BUILDER);

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .to("infinispan:remote_query?cacheContainer=#myCustomContainer");
                from("direct:noQueryResults")
                    .to("infinispan:remote_query?cacheContainer=#myCustomContainer&queryBuilder=#noResultQueryBuilder");
                from("direct:queryWithResults")
                    .to("infinispan:remote_query?cacheContainer=#myCustomContainer&queryBuilder=#withResultQueryBuilder");
            }
        };
    }

    @Override
    protected void doPreSetup() throws IOException {
        ConfigurationBuilder builder = new ConfigurationBuilder()
            .addServer()
                .host("localhost")
                .port(11222)
            .marshaller(new ProtoStreamMarshaller());

        manager = new RemoteCacheManager(builder.build());

        RemoteCache<String, String> metadataCache = manager.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
        metadataCache.put(
            "sample_bank_account/bank.proto",
            Util.read(InfinispanRemoteQueryProducerIT.class.getResourceAsStream("/sample_bank_account/bank.proto"))
        );

        MarshallerRegistration.registerMarshallers(ProtoStreamMarshaller.getSerializationContext(manager));
        SerializationContext serCtx = ProtoStreamMarshaller.getSerializationContext(manager);
        serCtx.registerProtoFiles(FileDescriptorSource.fromResources("/sample_bank_account/bank.proto"));
        serCtx.registerMarshaller(new UserMarshaller());
        serCtx.registerMarshaller(new GenderMarshaller());
    }

    @Override
    protected void doPostSetup() throws Exception {
        // pre-load data
        RemoteCache<Object, Object> cache = manager.getCache("remote_query");
        assertNotNull(cache);

        cache.clear();
        assertTrue(cache.isEmpty());

        for (final User user : USERS) {
            String key = createKey(user);
            cache.put(key, user);

            assertTrue(cache.containsKey(key));
        }
    }

    @Test
    public void producerQueryOperationWithoutQueryBuilder() throws Exception {
        Exchange request = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(OPERATION, QUERY);
            }
        });
        assertNull(request.getException());

        List<User> queryResult = request.getIn().getBody(List.class);
        assertNull(queryResult);
    }

    @Test
    public void producerQueryWithoutResult() throws Exception {
        producerQueryWithoutResult("direct:start", NO_RESULT_QUERY_BUILDER);
    }

    @Test
    public void producerQueryWithoutResultAndQueryBuilderFromConfig() throws Exception {
        producerQueryWithoutResult("direct:noQueryResults", null);
    }

    private void producerQueryWithoutResult(String endpoint, final InfinispanQueryBuilder builder) throws Exception {
        Exchange request = template.request(endpoint, createQueryProcessor(builder));

        assertNull(request.getException());

        List<User> queryResult = request.getIn().getBody(List.class);
        assertNotNull(queryResult);
        assertEquals(0, queryResult.size());
    }

    @Test
    public void producerQueryWithResult() throws Exception {
        producerQueryWithResult("direct:start", WITH_RESULT_QUERY_BUILDER);
    }

    @Test
    public void producerQueryWithResultAndQueryBuilderFromConfig() throws Exception {
        producerQueryWithResult("direct:queryWithResults", null);
    }

    private void producerQueryWithResult(String endpoint, final InfinispanQueryBuilder builder) throws Exception {
        Exchange request = template.request(endpoint, createQueryProcessor(builder));
        assertNull(request.getException());

        List<User> queryResult = request.getIn().getBody(List.class);
        assertNotNull(queryResult);
        assertEquals(2, queryResult.size());
        assertTrue(hasUser(queryResult, "nameA", "surnameA"));
        assertTrue(hasUser(queryResult, "nameA", "surnameB"));
    }

    private Processor createQueryProcessor(final InfinispanQueryBuilder builder) {
        return new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(OPERATION, QUERY);
                if (builder != null) {
                    exchange.getIn().setHeader(QUERY_BUILDER, builder);
                }
            }
        };
    }
}
