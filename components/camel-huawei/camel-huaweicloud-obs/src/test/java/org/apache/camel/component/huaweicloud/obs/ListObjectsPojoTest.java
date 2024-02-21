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

public class ListObjectsPojoTest extends CamelTestSupport {

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
        List<ObsObject> objects = new ArrayList<>();
        ObsObject object1 = new ObsObject();
        object1.setBucketName(testConfiguration.getProperty("bucketName"));
        object1.setObjectKey("Object 1");
        ObsObject object2 = new ObsObject();
        object2.setBucketName(testConfiguration.getProperty("bucketName"));
        object2.setObjectKey("Object 2");
        objects.add(object1);
        objects.add(object2);
        ObjectListing listing = new ObjectListing(objects, null, null, false, null, null, 0, null, null, null);
        Mockito.when(mockClient.listObjects(Mockito.any(ListObjectsRequest.class))).thenReturn(listing);

        MockEndpoint mock = getMockEndpoint("mock:list_objects_result");
        mock.expectedMinimumMessageCount(1);

        ListObjectsRequest request = new ListObjectsRequest(testConfiguration.getProperty("bucketName"));
        template.sendBody("direct:list_objects", request);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals(
                "[{\"bucketName\":\"dummy_bucket_name\",\"objectKey\":\"Object 1\"},{\"bucketName\":\"dummy_bucket_name\",\"objectKey\":\"Object 2\"}]",
                responseExchange.getIn().getBody(String.class));
    }
}
