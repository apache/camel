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
package org.apache.camel.component.ibm.cos.integration;

import java.util.UUID;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for IBM COS integration tests. Tests are only enabled when the following system properties are set:
 * <ul>
 * <li>camel.ibm.cos.apiKey - IBM Cloud API Key</li>
 * <li>camel.ibm.cos.serviceInstanceId - IBM COS Service Instance ID (CRN)</li>
 * <li>camel.ibm.cos.endpointUrl - IBM COS Endpoint URL</li>
 * </ul>
 * Optional:
 * <ul>
 * <li>camel.ibm.cos.location - IBM COS Location/Region (default: us-south)</li>
 * </ul>
 * <p>
 * Example usage: mvn verify -Dcamel.ibm.cos.apiKey=xxx -Dcamel.ibm.cos.serviceInstanceId=yyy
 * -Dcamel.ibm.cos.endpointUrl=https://s3.us-south.cloud-object-storage.appdomain.cloud
 */
public class IBMCOSTestSupport extends CamelTestSupport {

    protected static AmazonS3 cosClient;
    protected static String bucketName;

    @BeforeAll
    public static void setUpCosClientAndBucket() {
        String apiKey = getProperty("camel.ibm.cos.apiKey");
        String serviceInstanceId = getProperty("camel.ibm.cos.serviceInstanceId");
        String endpointUrl = getProperty("camel.ibm.cos.endpointUrl");

        AWSCredentials credentials = new BasicIBMOAuthCredentials(apiKey, serviceInstanceId);
        ClientConfiguration clientConfig = new ClientConfiguration()
                .withRequestTimeout(5000)
                .withTcpKeepAlive(true);

        // For IBM COS, use null as the signing region and let the SDK determine it from the endpoint
        cosClient = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointUrl, null))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfig)
                .build();

        // Create unique bucket for this test class
        bucketName = "camel-test-" + UUID.randomUUID().toString().substring(0, 12).toLowerCase();

        try {
            cosClient.createBucket(bucketName);
        } catch (Exception e) {
            throw e;
        }
    }

    @AfterAll
    public static void tearDownTestBucket() {
        if (cosClient != null && bucketName != null) {
            try {
                // Delete all objects in the bucket
                cosClient.listObjectsV2(bucketName).getObjectSummaries()
                        .forEach(obj -> cosClient.deleteObject(bucketName, obj.getKey()));

                // Delete the bucket
                cosClient.deleteBucket(bucketName);
            } catch (Exception e) {
                // Don't fail the test on cleanup errors
            }
        }
    }

    protected static String getProperty(String name) {
        return System.getProperty(name);
    }

    protected static String getApiKey() {
        return getProperty("camel.ibm.cos.apiKey");
    }

    protected static String getServiceInstanceId() {
        return getProperty("camel.ibm.cos.serviceInstanceId");
    }

    protected static String getEndpointUrl() {
        return getProperty("camel.ibm.cos.endpointUrl");
    }

    protected static String getLocation() {
        return System.getProperty("camel.ibm.cos.location", "us-south");
    }

    protected String buildEndpointUri() {
        return String.format(
                "ibm-cos://%s?apiKey=RAW(%s)&serviceInstanceId=RAW(%s)&endpointUrl=%s&location=%s",
                bucketName,
                getApiKey(),
                getServiceInstanceId(),
                getEndpointUrl(),
                getLocation());
    }

    protected String buildEndpointUri(String operation) {
        return buildEndpointUri() + "&operation=" + operation;
    }
}
