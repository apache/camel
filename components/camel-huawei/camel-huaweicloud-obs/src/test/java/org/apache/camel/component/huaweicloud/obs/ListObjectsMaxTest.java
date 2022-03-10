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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obs.services.ObsClient;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObsObject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ListObjectsMaxTest extends CamelTestSupport {

    TestConfiguration testConfiguration = new TestConfiguration();

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
                from("direct:list_objects")
                        .setProperty("CamelHwCloudObsBucketName", constant(testConfiguration.getProperty("bucketName")))
                        .to("hwcloud-obs:listObjects?" +
                            "serviceKeys=#serviceKeys" +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&obsClient=#obsClient")
                        .log("List objects successful")
                        .to("mock:list_objects_result");
            }
        };
    }

    @Test
    public void testListObjects() throws Exception {
        int len = 2047;
        List<ObsObject> full1 = new ArrayList<>();
        List<ObsObject> full2 = new ArrayList<>();
        List<ObsObject> half = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            ObsObject object = new ObsObject();
            object.setBucketName(testConfiguration.getProperty("bucketName"));
            object.setObjectKey("Object " + i);
            if (i < 1000) {
                full1.add(object);
            } else if (i < 2000) {
                full2.add(object);
            } else {
                half.add(object);
            }
        }

        ObjectListing listing1 = new ObjectListing(full1, null, null, true, null, null, 1000, null, "Object 999", null);
        ObjectListing listing2 = new ObjectListing(full2, null, null, true, null, null, 1000, null, "Object 1999", null);
        ObjectListing listing3 = new ObjectListing(half, null, null, false, null, null, 1000, null, null, null);
        Mockito.when(mockClient.listObjects(Mockito.any(ListObjectsRequest.class))).thenReturn(listing1, listing2, listing3);

        MockEndpoint mock = getMockEndpoint("mock:list_objects_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:list_objects", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        List<ObsObject> result = new ObjectMapper().readValue(responseExchange.getIn().getBody(String.class),
                new TypeReference<List<ObsObject>>() {
                });
        assertEquals(len, result.size());
        for (int i = 0; i < len; i++) {
            assertEquals(testConfiguration.getProperty("bucketName"), result.get(i).getBucketName());
            assertEquals("Object " + i, result.get(i).getObjectKey());
        }
    }
}
