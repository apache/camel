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
package org.apache.camel.component.kudu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.Schema;
import org.apache.kudu.Type;
import org.apache.kudu.client.CreateTableOptions;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KuduClientAutowiredTest extends AbstractKuduTest {
    @EndpointInject(value = "mock:result")
    public MockEndpoint successEndpoint;

    @BeforeEach
    public void resetEndpoints() {
        successEndpoint.reset();
    }

    @Test
    void autowiredKuduClient() throws InterruptedException, KuduException {
        KuduComponent component = context.getComponent("kudu", KuduComponent.class);
        Assertions.assertEquals(ikc.getClient(), component.getKuduClient());

        successEndpoint.expectedMessageCount(1);

        final Map<String, Object> headers = new HashMap<>();

        List<ColumnSchema> columns = new ArrayList<>(5);
        final List<String> columnNames = Arrays.asList("id", "title", "name", "lastname", "address");

        for (int i = 0; i < columnNames.size(); i++) {
            columns.add(
                    new ColumnSchema.ColumnSchemaBuilder(columnNames.get(i), Type.STRING)
                            .key(i == 0)
                            .build());
        }

        List<String> rangeKeys = new ArrayList<>();
        rangeKeys.add("id");

        headers.put(KuduConstants.CAMEL_KUDU_SCHEMA, new Schema(columns));
        headers.put(KuduConstants.CAMEL_KUDU_TABLE_OPTIONS, new CreateTableOptions().setRangePartitionColumns(rangeKeys));

        template.requestBodyAndHeaders("direct://create", null, headers);

        successEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:create")
                        .to("kudu:localhost:7051/TestTable?operation=create_table")
                        .to("mock:result");
            }
        };
    }

    @BindToRegistry("kuduClient")
    public KuduClient createKuduClient() {
        return ikc.getClient();
    }
}
