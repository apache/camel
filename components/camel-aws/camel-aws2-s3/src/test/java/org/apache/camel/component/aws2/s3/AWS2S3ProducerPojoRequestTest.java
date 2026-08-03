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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * When {@code pojoRequest=true}, the producer must fail fast if the body is not the expected request type, rather than
 * silently doing nothing (see CAMEL-24261).
 */
class AWS2S3ProducerPojoRequestTest {

    @ParameterizedTest
    @CsvSource({
            "copyObject,copyObject operation requires CopyObjectRequest in POJO mode",
            "deleteObject,deleteObject operation requires DeleteObjectRequest in POJO mode",
            "deleteBucket,deleteBucket operation requires DeleteBucketRequest in POJO mode",
            "getObject,getObject operation requires GetObjectRequest in POJO mode",
            "getObjectRange,getObjectRange operation requires GetObjectRequest in POJO mode",
            "listObjects,listObjects operation requires ListObjectsV2Request in POJO mode",
            "deleteObjects,deleteObjects operation requires DeleteObjectsRequest in POJO mode",
            "restoreObject,restoreObject operation requires RestoreObjectRequest in POJO mode",
            "getObjectTagging,getObjectTagging operation requires GetObjectTaggingRequest in POJO mode",
            "putObjectTagging,putObjectTagging operation requires PutObjectTaggingRequest in POJO mode",
            "deleteObjectTagging,deleteObjectTagging operation requires DeleteObjectTaggingRequest in POJO mode",
            "getObjectAcl,getObjectAcl operation requires GetObjectAclRequest in POJO mode",
            "putObjectAcl,putObjectAcl operation requires PutObjectAclRequest in POJO mode",
            "createBucket,createBucket operation requires CreateBucketRequest in POJO mode",
            "getBucketTagging,getBucketTagging operation requires GetBucketTaggingRequest in POJO mode",
            "putBucketTagging,putBucketTagging operation requires PutBucketTaggingRequest in POJO mode",
            "deleteBucketTagging,deleteBucketTagging operation requires DeleteBucketTaggingRequest in POJO mode",
            "getBucketVersioning,getBucketVersioning operation requires GetBucketVersioningRequest in POJO mode",
            "putBucketVersioning,putBucketVersioning operation requires PutBucketVersioningRequest in POJO mode",
            "getBucketPolicy,getBucketPolicy operation requires GetBucketPolicyRequest in POJO mode",
            "putBucketPolicy,putBucketPolicy operation requires PutBucketPolicyRequest in POJO mode",
            "deleteBucketPolicy,deleteBucketPolicy operation requires DeleteBucketPolicyRequest in POJO mode",
    })
    void pojoRequestWithWrongBodyTypeThrows(String operation, String expectedMessage) throws Exception {
        AWS2S3Configuration configuration = new AWS2S3Configuration();
        configuration.setPojoRequest(true);
        configuration.setOperation(AWS2S3Operations.valueOf(operation));
        // some operations resolve the bucket/key before the pojo-type check; supply them so the wrong-typed
        // body reaches the instanceof guard we are exercising
        configuration.setBucketName("test-bucket");
        configuration.setKeyName("test-key");

        AWS2S3Endpoint endpoint = mock(AWS2S3Endpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getS3Client()).thenReturn(mock(S3Client.class));

        AWS2S3Producer producer = new AWS2S3Producer(endpoint);

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody("not the expected request type");

        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }
}
