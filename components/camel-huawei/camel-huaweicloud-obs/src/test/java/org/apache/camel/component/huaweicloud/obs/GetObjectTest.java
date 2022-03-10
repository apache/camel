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
package org.apache.camel.component.huaweicloud.obs;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;

import com.obs.services.ObsClient;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.obs.constants.OBSHeaders;
import org.apache.camel.component.huaweicloud.obs.constants.OBSProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetObjectTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GetObjectTest.class.getName());

    TestConfiguration testConfiguration = new TestConfiguration();

    String bucketName = "reji-test";
    String objectName = "test_file.txt";

    @BindToRegistry("obsClient")
    ObsClient mockClient = Mockito.mock(ObsClient.class);

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(
            testConfiguration.getProperty("accessKey"),
            testConfiguration.getProperty("secretKey"));

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:get_object")
                        .setProperty(OBSProperties.BUCKET_NAME, constant(bucketName))
                        .setProperty(OBSProperties.OBJECT_NAME, constant(objectName))
                        .to("hwcloud-obs:getObject?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&obsClient=#obsClient")
                        .log("Get object successful")
                        .to("mock:get_object_result");
            }
        };
    }

    @Test
    public void testGetObject() throws Exception {

        ObsObject response = new ObsObject();
        response.setBucketName(bucketName);
        response.setObjectKey(objectName);

        File initialFile = new File("src/test/resources/files/test_file.txt");
        InputStream stream = new FileInputStream(initialFile);
        response.setObjectContent(stream);

        Date testDate = new Date();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setLastModified(testDate);
        metadata.setContentLength(9L);
        metadata.setContentType("text/plain");
        metadata.setEtag("eb733a00c0c9d336e65691a37ab54293");
        metadata.setContentMd5("63M6AMDJ0zbmVpGjerVCkw==");
        response.setMetadata(metadata);

        Mockito.when(mockClient.getObject(bucketName, objectName)).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:get_object_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:get_object", "dummy");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals(9L, responseExchange.getIn().getHeader(Exchange.CONTENT_LENGTH));
        assertEquals("text/plain", responseExchange.getIn().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("eb733a00c0c9d336e65691a37ab54293", responseExchange.getIn().getHeader(OBSHeaders.ETAG));
        assertEquals("63M6AMDJ0zbmVpGjerVCkw==", responseExchange.getIn().getHeader(OBSHeaders.CONTENT_MD5));
        assertEquals(testDate, responseExchange.getIn().getHeader(OBSHeaders.LAST_MODIFIED));

        assertEquals(bucketName, responseExchange.getIn().getHeader(OBSHeaders.BUCKET_NAME));
        assertEquals(objectName, responseExchange.getIn().getHeader(OBSHeaders.OBJECT_KEY));
        assertEquals(objectName, responseExchange.getIn().getHeader(Exchange.FILE_NAME));

    }
}
