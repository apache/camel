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
package org.apache.camel.component.alibaba.ots;

import java.util.List;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.ListTableResponse;
import com.alicloud.openservices.tablestore.model.Response;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlibabaOtsProducerTest extends CamelTestSupport {

    @Test
    void processReinitializesClientFromEndpointOnEachExchange() throws Exception {
        AlibabaOtsEndpoint endpoint = mock(AlibabaOtsEndpoint.class);
        when(endpoint.getOperation()).thenReturn("listTables");
        when(endpoint.getCamelContext()).thenReturn(context);

        SyncClient firstClient = mock(SyncClient.class);
        SyncClient secondClient = mock(SyncClient.class);
        when(endpoint.initClient()).thenReturn(firstClient, secondClient);

        ListTableResponse response = new ListTableResponse(new Response("req-list"));
        response.setTableNames(List.of("table-a"));
        when(firstClient.listTable()).thenReturn(response);
        when(secondClient.listTable()).thenReturn(response);

        AlibabaOtsProducer producer = new AlibabaOtsProducer(endpoint);
        producer.process(new DefaultExchange(context));
        producer.process(new DefaultExchange(context));

        verify(firstClient).listTable();
        verify(secondClient).listTable();
    }
}
