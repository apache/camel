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
package org.apache.camel.component.milvus;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import io.milvus.client.MilvusClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.UpsertParam;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The action option of the endpoint selects the action, and the CamelMilvusAction header wins over it. A stub client
 * records the calls, so no Milvus server is needed.
 */
public class MilvusActionOptionTest extends CamelTestSupport {

    private final List<String> calls = new ArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        MilvusClient client = (MilvusClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { MilvusClient.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "insert", "upsert" -> {
                        calls.add(method.getName());
                        yield R.success(MutationResult.getDefaultInstance());
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        context.getComponent(Milvus.SCHEME, MilvusComponent.class).getConfiguration().setClient(client);
        return context;
    }

    @Test
    public void actionOption() {
        Exchange result = fluentTemplate.to("milvus:test?action=INSERT")
                .withBody(insertParam())
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(calls).containsExactly("insert");
        assertThat(result.getMessage().getBody()).isInstanceOf(MutationResult.class);
    }

    @Test
    public void headerWinsOverActionOption() {
        Exchange result = fluentTemplate.to("milvus:test?action=INSERT")
                .withHeader(MilvusHeaders.ACTION, MilvusAction.UPSERT)
                .withBody(upsertParam())
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(calls).containsExactly("upsert");
    }

    @Test
    public void noAction() {
        Exchange result = fluentTemplate.to("milvus:test")
                .withBody(insertParam())
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
        assertThat(calls).isEmpty();
    }

    private static InsertParam insertParam() {
        return InsertParam.newBuilder()
                .withCollectionName("test")
                .withFields(List.of(new InsertParam.Field("id", List.of(1L))))
                .build();
    }

    private static UpsertParam upsertParam() {
        return UpsertParam.newBuilder()
                .withCollectionName("test")
                .withFields(List.of(new InsertParam.Field("id", List.of(1L))))
                .build();
    }
}
