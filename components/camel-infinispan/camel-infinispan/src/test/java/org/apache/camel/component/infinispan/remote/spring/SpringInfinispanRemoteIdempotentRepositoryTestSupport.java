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
package org.apache.camel.component.infinispan.remote.spring;

import java.util.UUID;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.MarshallerUtil;
import org.infinispan.query.remote.client.impl.MarshallerRegistration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class SpringInfinispanRemoteIdempotentRepositoryTestSupport extends CamelSpringTestSupport {
    @RegisterExtension
    public static InfinispanService service = InfinispanServiceFactory.createService();

    @Override
    public void doPreSetup() throws Exception {
        ConfigurationBuilder clientBuilder = new ConfigurationBuilder();

        // for default tests, we force return value for all the
        // operations
        clientBuilder
                .forceReturnValues(true);

        // add server from the test infra service
        clientBuilder
                .addServer()
                .host(service.host())
                .port(service.port());

        // add security info
        clientBuilder
                .security()
                .authentication()
                .username(service.username())
                .password(service.password())
                .serverName("infinispan")
                .saslMechanism("DIGEST-MD5")
                .realm("default");

        RemoteCacheManager manager = new RemoteCacheManager(clientBuilder.create());
        MarshallerRegistration.init(MarshallerUtil.getSerializationContext(manager));
        RemoteCache<Object, Object> cache = manager.administration().getOrCreateCache("idempotent", (String) null);
        assertNotNull(cache);
        super.doPreSetup();
    }

    @Test
    public void testIdempotent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String messageId = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            template.sendBodyAndHeader("direct:start", UUID.randomUUID().toString(), "MessageId", messageId);
        }

        mock.assertIsSatisfied();
    }
}
