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

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obs.services.ObsClient;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.StorageClassEnum;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.obs.constants.OBSProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PutObjectStringTest extends CamelTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(PutObjectTest.class);

    TestConfiguration testConfiguration = new TestConfiguration();

    ObjectMapper mapper = new ObjectMapper();

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
                from("direct:put_object")
                        .setBody(constant("a test string"))
                        .setProperty(OBSProperties.OBJECT_NAME, constant("string_file.txt"))
                        .setProperty(OBSProperties.BUCKET_NAME, constant("reji-abc"))
                        .setProperty(OBSProperties.BUCKET_LOCATION, constant("cn-north-1"))
                        .to("hwcloud-obs:putObject?" +
                            "serviceKeys=#serviceKeys" +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&obsClient=#obsClient")
                        .log("Put object successful")
                        .to("log:LOG?showAll=true")
                        .to("mock:put_object_result");
            }
        };
    }

    @Test
    public void putObjectStringTest() throws Exception {

        PutObjectResult putObjectResult = new PutObjectResult(
                "reji-abc", "string_file.txt",
                "eb733a00c0c9d336e65691a37ab54293", "version-xxx",
                StorageClassEnum.STANDARD, "https://reji-abc.obs.cn-north-1.myhuaweicloud.com/test_file.txt");

        Mockito.when(mockClient.putObject(Mockito.any(String.class),
                Mockito.any(String.class), Mockito.any(InputStream.class)))
                .thenReturn(putObjectResult);

        MockEndpoint mock = getMockEndpoint("mock:put_object_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:put_object", "sample file content");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals("{\"bucketName\":\"reji-abc\",\"objectKey\":\"string_file.txt\"," +
                     "\"etag\":\"eb733a00c0c9d336e65691a37ab54293\",\"versionId\":\"version-xxx\"," +
                     "\"storageClass\":\"STANDARD\"," +
                     "\"objectUrl\":\"https://reji-abc.obs.cn-north-1.myhuaweicloud.com/test_file.txt\"," +
                     "\"statusCode\":0}",
                responseExchange.getIn().getBody());

    }
}
