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
package org.apache.camel.component.alibaba.kms;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.GenerateDataKeyRequest;
import com.aliyun.kms20160120.models.GenerateDataKeyResponse;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KMSProducerTest extends CamelTestSupport {

    @Test
    void processReinitializesClientFromEndpointOnEachExchange() throws Exception {
        KMSEndpoint endpoint = mock(KMSEndpoint.class);
        when(endpoint.getOperation()).thenReturn("generateDataKey");
        when(endpoint.getKeyId()).thenReturn("demo-key");
        when(endpoint.getCamelContext()).thenReturn(context);

        Client firstClient = mock(Client.class);
        Client secondClient = mock(Client.class);
        when(endpoint.initClient()).thenReturn(firstClient, secondClient);

        GenerateDataKeyResponse response = new GenerateDataKeyResponse();
        when(firstClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(response);
        when(secondClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(response);

        KMSProducer producer = new KMSProducer(endpoint);
        producer.process(new DefaultExchange(context));
        producer.process(new DefaultExchange(context));

        verify(firstClient).generateDataKey(any(GenerateDataKeyRequest.class));
        verify(secondClient).generateDataKey(any(GenerateDataKeyRequest.class));
    }
}
