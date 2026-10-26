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
package org.apache.camel.component.minio;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.ServerSideEncryption;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioProducerEncryptionTest extends CamelTestSupport {

    private MinioClient minioClient;
    private SecretKey secretKey;

    @BeforeEach
    void init() throws Exception {
        minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        secretKey = generator.generateKey();
    }

    private MinioProducer createProducer(MinioEndpoint endpoint) throws Exception {
        endpoint.start();
        MinioProducer producer = new MinioProducer(endpoint);
        producer.start();
        return producer;
    }

    @Test
    void putObjectWithServerSideEncryption() throws Exception {
        ServerSideEncryption.S3 sse = new ServerSideEncryption.S3();
        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component.createEndpoint("minio://mybucket?autoCreateBucket=false");
        endpoint.getConfiguration().setMinioClient(minioClient);
        endpoint.getConfiguration().setServerSideEncryption(sse);

        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        MinioProducer producer = createProducer(endpoint);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
        exchange.getIn().setBody("content");

        producer.process(exchange);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertSame(sse, captor.getValue().sse());
        producer.stop();
        endpoint.stop();
    }

    @Test
    void putObjectWithCustomerKey() throws Exception {
        ServerSideEncryption.CustomerKey ssec = new ServerSideEncryption.CustomerKey(secretKey);
        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component.createEndpoint("minio://mybucket?autoCreateBucket=false");
        endpoint.getConfiguration().setMinioClient(minioClient);
        endpoint.getConfiguration().setServerSideEncryptionCustomerKey(ssec);

        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        MinioProducer producer = createProducer(endpoint);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
        exchange.getIn().setBody("content");

        producer.process(exchange);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertSame(ssec, captor.getValue().sse());
        producer.stop();
        endpoint.stop();
    }

    @Test
    void putObjectWithServerSideEncryptionHeader() throws Exception {
        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component.createEndpoint("minio://mybucket?autoCreateBucket=false");
        endpoint.getConfiguration().setMinioClient(minioClient);

        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        MinioProducer producer = createProducer(endpoint);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
        exchange.getIn().setHeader(MinioConstants.SERVER_SIDE_ENCRYPTION, "AES256");
        exchange.getIn().setBody("content");

        producer.process(exchange);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertNotNull(captor.getValue().extraHeaders());
        assertEquals("AES256", captor.getValue().extraHeaders().getFirst("x-amz-server-side-encryption"));
        producer.stop();
        endpoint.stop();
    }

    @Test
    void copyObjectWithEncryption() throws Exception {
        ServerSideEncryption.S3 sse = new ServerSideEncryption.S3();
        ServerSideEncryption.CustomerKey ssec = new ServerSideEncryption.CustomerKey(secretKey);

        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component.createEndpoint("minio://mybucket?autoCreateBucket=false");
        endpoint.getConfiguration().setMinioClient(minioClient);
        endpoint.getConfiguration().setServerSideEncryption(sse);
        endpoint.getConfiguration().setServerSideEncryptionCustomerKey(ssec);

        when(minioClient.copyObject(any(CopyObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        MinioProducer producer = createProducer(endpoint);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.copyObject);
        exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "source.txt");
        exchange.getIn().setHeader(MinioConstants.DESTINATION_OBJECT_NAME, "dest.txt");
        exchange.getIn().setHeader(MinioConstants.DESTINATION_BUCKET_NAME, "dest-bucket");

        producer.process(exchange);

        ArgumentCaptor<CopyObjectArgs> captor = ArgumentCaptor.forClass(CopyObjectArgs.class);
        verify(minioClient).copyObject(captor.capture());
        assertSame(ssec, captor.getValue().sse());
        assertNotNull(captor.getValue().source());
        assertSame(ssec, captor.getValue().source().ssec());
        producer.stop();
        endpoint.stop();
    }

    @Test
    void getObjectWithCustomerKey() throws Exception {
        ServerSideEncryption.CustomerKey ssec = new ServerSideEncryption.CustomerKey(secretKey);

        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component.createEndpoint("minio://mybucket?autoCreateBucket=false");
        endpoint.getConfiguration().setMinioClient(minioClient);
        endpoint.getConfiguration().setServerSideEncryptionCustomerKey(ssec);

        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mock(GetObjectResponse.class));

        MinioProducer producer = createProducer(endpoint);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.getObject);
        exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");

        producer.process(exchange);

        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertSame(ssec, captor.getValue().ssec());
        producer.stop();
        endpoint.stop();
    }

    @Test
    void getObjectRangeWithCustomerKey() throws Exception {
        ServerSideEncryption.CustomerKey ssec = new ServerSideEncryption.CustomerKey(secretKey);

        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component.createEndpoint("minio://mybucket?autoCreateBucket=false");
        endpoint.getConfiguration().setMinioClient(minioClient);
        endpoint.getConfiguration().setServerSideEncryptionCustomerKey(ssec);

        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mock(GetObjectResponse.class));

        MinioProducer producer = createProducer(endpoint);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.getObjectRange);
        exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
        exchange.getIn().setHeader(MinioConstants.OFFSET, "10");
        exchange.getIn().setHeader(MinioConstants.LENGTH, "20");

        producer.process(exchange);

        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertSame(ssec, captor.getValue().ssec());
        assertEquals(10L, captor.getValue().offset());
        assertEquals(20L, captor.getValue().length());
        producer.stop();
        endpoint.stop();
    }
}
