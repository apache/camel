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

package org.apache.camel.component.infinispan.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.MarshallerUtil;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.domain.User;
import org.infinispan.protostream.domain.marshallers.GenderMarshaller;
import org.infinispan.protostream.domain.marshallers.UserMarshaller;
import org.infinispan.query.remote.client.impl.MarshallerRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InfinispanRemoteQueryConsumerIT extends InfinispanRemoteQueryTestSupport {
    @BindToRegistry("continuousQueryBuilder")
    private InfinispanQueryBuilder continuousQueryBuilder =
            qf -> qf.query("FROM sample_bank_account.User WHERE name LIKE 'CQ%'");

    @BindToRegistry("continuousQueryBuilderNoMatch")
    private InfinispanQueryBuilder continuousQueryBuilderNoMatch =
            qf -> qf.query("FROM sample_bank_account.User WHERE name LIKE '%TEST%'");

    @BindToRegistry("continuousQueryBuilderAll")
    private InfinispanQueryBuilder continuousQueryBuilderAll =
            qf -> qf.query("FROM sample_bank_account.User WHERE name LIKE '%Q0%'");

    // *****************************
    //
    // *****************************

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
            continuousQuery
                    .message(i)
                    .header(InfinispanConstants.CACHE_NAME)
                    .isEqualTo(getCache().getName());
            if (i >= 2) {
                continuousQuery
                        .message(i)
                        .header(InfinispanConstants.EVENT_TYPE)
                        .isEqualTo(InfinispanConstants.CACHE_ENTRY_LEAVING);
                continuousQuery
                        .message(i)
                        .header(InfinispanConstants.EVENT_DATA)
                        .isNull();
            } else {
                continuousQuery
                        .message(i)
                        .header(InfinispanConstants.EVENT_TYPE)
                        .isEqualTo(InfinispanConstants.CACHE_ENTRY_JOINING);
                continuousQuery
                        .message(i)
                        .header(InfinispanConstants.EVENT_DATA)
                        .isNotNull();
                continuousQuery
                        .message(i)
                        .header(InfinispanConstants.EVENT_DATA)
                        .isInstanceOf(User.class);
            }
        }

        for (final User user : CQ_USERS) {
            getCache().put(createKey(user), user);
        }

        assertEquals(CQ_USERS.length, getCache().size());

        for (final User user : CQ_USERS) {
            getCache().remove(createKey(user));
        }

        assertTrue(getCache().isEmpty());

        continuousQuery.assertIsSatisfied();
        continuousQueryBuilderNoMatch.assertIsSatisfied();
        continuousQueryBuilderAll.assertIsSatisfied();
    }

    // *****************************
    //
    // *****************************

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        cacheContainer
                .administration()
                .schemas()
                .create(FileDescriptorSource.fromResources("sample_bank_account/bank.proto"));

        MarshallerRegistration.init(MarshallerUtil.getSerializationContext(cacheContainer));
        SerializationContext serCtx = MarshallerUtil.getSerializationContext(cacheContainer);
        serCtx.registerProtoFiles(FileDescriptorSource.fromResources("/sample_bank_account/bank.proto"));
        serCtx.registerMarshaller(new UserMarshaller());
        serCtx.registerMarshaller(new GenderMarshaller());
    }

    @Override
    protected ConfigurationBuilder getConfiguration() {
        ConfigurationBuilder builder = super.getConfiguration();
        builder.marshaller(new ProtoStreamMarshaller());

        return builder;
    }

    @BeforeEach
    protected void beforeEach() {
        // cleanup the default test cache before each run
        getCache().clear();

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> cacheContainer.isStarted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("infinispan:%s?queryBuilder=#continuousQueryBuilder", getCacheName())
                        .to("mock:continuousQuery");
                fromF("infinispan:%s?queryBuilder=#continuousQueryBuilderNoMatch", getCacheName())
                        .to("mock:continuousQueryNoMatch");
                fromF("infinispan:%s?queryBuilder=#continuousQueryBuilderAll", getCacheName())
                        .to("mock:continuousQueryAll");
            }
        };
    }
}
