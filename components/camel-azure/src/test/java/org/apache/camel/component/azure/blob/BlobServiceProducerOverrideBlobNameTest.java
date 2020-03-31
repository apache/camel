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
package org.apache.camel.component.azure.blob;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.azure.blob.BlobHeadersConstants.OVERRIDE_BLOB_NAME;

public class BlobServiceProducerOverrideBlobNameTest {

    private Exchange exchange;
    private BlobServiceProducer producer;

    @Before
    public void setUp() {
        CamelContext context = new DefaultCamelContext();
        BlobServiceEndpoint endpoint = (BlobServiceEndpoint) context.getEndpoint("azure-blob://camelazure/container/blob?credentialsAccountKey=aKey&credentialsAccountName=name");
        exchange = new DefaultExchange(context);
        producer = new BlobServiceProducer(endpoint);
    }

    @Test
    public void testOverrideBlobName() throws Exception {
        String blobName = "myBlobName";
        exchange.getIn().setHeader(OVERRIDE_BLOB_NAME, blobName);

        producer.process(exchange);
    }

    @Test
    public void testSetBlobNameFromEndpoint() throws Exception {
        String blobName = "blob";
        exchange.getIn().setHeader(OVERRIDE_BLOB_NAME, blobName);

        producer.process(exchange);
    }

}
