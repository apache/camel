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
package org.apache.camel.component.aws2.s3;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the {@code CamelAwsS3Grant*} headers are applied as explicit ACL grants on the {@code PutObject}
 * request (CAMEL-16809).
 */
class AWS2S3ProducerAclGrantTest {

    @Mock
    private AWS2S3Endpoint endpoint;

    @Mock
    private AWS2S3Configuration configuration;

    @Mock
    private S3Client s3Client;

    private AWS2S3Producer producer;
    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getS3Client()).thenReturn(s3Client);
        when(configuration.getBucketName()).thenReturn("test-bucket");
        producer = new AWS2S3Producer(endpoint);
    }

    private void mockPutObject() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn((PutObjectResponse) PutObjectResponse.builder()
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build())
                        .build());
    }

    private PutObjectRequest capturePutObjectRequest() {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        return captor.getValue();
    }

    @Test
    void putObjectShouldApplyAclGrantHeaders() throws Exception {
        mockPutObject();

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(AWS2S3Constants.KEY, "my-key");
        exchange.getIn().setHeader(AWS2S3Constants.GRANT_FULL_CONTROL, "id=canonical-full");
        exchange.getIn().setHeader(AWS2S3Constants.GRANT_READ, "id=canonical-read");
        exchange.getIn().setHeader(AWS2S3Constants.GRANT_READ_ACP, "emailAddress=readacp@example.com");
        exchange.getIn().setHeader(AWS2S3Constants.GRANT_WRITE_ACP,
                "uri=http://acs.amazonaws.com/groups/global/AllUsers");
        exchange.getIn().setBody("hello grant");

        producer.process(exchange);

        PutObjectRequest request = capturePutObjectRequest();
        assertThat(request.grantFullControl()).isEqualTo("id=canonical-full");
        assertThat(request.grantRead()).isEqualTo("id=canonical-read");
        assertThat(request.grantReadACP()).isEqualTo("emailAddress=readacp@example.com");
        assertThat(request.grantWriteACP()).isEqualTo("uri=http://acs.amazonaws.com/groups/global/AllUsers");
    }

    @Test
    void putObjectWithoutGrantHeadersLeavesGrantsUnset() throws Exception {
        mockPutObject();

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(AWS2S3Constants.KEY, "my-key");
        exchange.getIn().setBody("hello");

        producer.process(exchange);

        PutObjectRequest request = capturePutObjectRequest();
        assertThat(request.grantFullControl()).isNull();
        assertThat(request.grantRead()).isNull();
        assertThat(request.grantReadACP()).isNull();
        assertThat(request.grantWriteACP()).isNull();
    }
}
