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
package org.apache.camel.component.alibaba.sls;

import com.aliyun.sls20201230.Client;
import com.aliyun.sls20201230.models.ListLogStoresRequest;
import com.aliyun.sls20201230.models.ListLogStoresResponse;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlibabaSlsProducerTest extends CamelTestSupport {

    @Test
    void processReinitializesClientFromEndpointOnEachExchange() throws Exception {
        AlibabaSlsEndpoint endpoint = mock(AlibabaSlsEndpoint.class);
        when(endpoint.getOperation()).thenReturn("listLogStores");
        when(endpoint.getProject()).thenReturn("demo-project");
        when(endpoint.getCamelContext()).thenReturn(context);

        Client firstClient = mock(Client.class);
        Client secondClient = mock(Client.class);
        when(endpoint.initClient()).thenReturn(firstClient, secondClient);

        ListLogStoresResponse response = new ListLogStoresResponse();
        when(firstClient.listLogStores(eq("demo-project"), any(ListLogStoresRequest.class))).thenReturn(response);
        when(secondClient.listLogStores(eq("demo-project"), any(ListLogStoresRequest.class))).thenReturn(response);

        AlibabaSlsProducer producer = new AlibabaSlsProducer(endpoint);
        producer.process(new DefaultExchange(context));
        producer.process(new DefaultExchange(context));

        verify(firstClient).listLogStores(eq("demo-project"), any(ListLogStoresRequest.class));
        verify(secondClient).listLogStores(eq("demo-project"), any(ListLogStoresRequest.class));
    }
}
