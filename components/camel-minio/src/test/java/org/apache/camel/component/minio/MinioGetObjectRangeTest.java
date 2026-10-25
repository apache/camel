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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The operation getObjectRange, as the catalog and the documentation name it, gets a byte range of an object, set as
 * the operation option or as the CamelMinioOperation header. The former name getPartialObject still works.
 */
class MinioGetObjectRangeTest extends CamelTestSupport {

    private FakeS3Server s3;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("bkt");
        s3.put("a.txt", "Hello World");
        s3.start();
        return super.createCamelContext();
    }

    @AfterEach
    void stopServer() {
        if (s3 != null) {
            s3.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minio = "minio://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&endpoint=" + s3.endpoint();
                from("direct:range").to(minio + "&operation=getObjectRange").convertBodyTo(String.class);
                from("direct:partial").to(minio + "&operation=getPartialObject").convertBodyTo(String.class);
                from("direct:header").to(minio + "&operation=getObject").convertBodyTo(String.class);
            }
        };
    }

    private Map<String, Object> rangeHeaders() {
        return Map.of(MinioConstants.OBJECT_NAME, "a.txt", MinioConstants.OFFSET, "6", MinioConstants.LENGTH, "5");
    }

    @Test
    void getObjectRangeOption() {
        assertEquals("World", template.requestBodyAndHeaders("direct:range", null, rangeHeaders(), String.class));
    }

    @Test
    void getPartialObjectOptionStillWorks() {
        assertEquals("World", template.requestBodyAndHeaders("direct:partial", null, rangeHeaders(), String.class));
    }

    @Test
    void getObjectRangeHeader() {
        Map<String, Object> headers = new HashMap<>(rangeHeaders());
        headers.put(MinioConstants.MINIO_OPERATION, "getObjectRange");
        assertEquals("World", template.requestBodyAndHeaders("direct:header", null, headers, String.class));
    }

    @Test
    void getPartialObjectHeaderStillWorks() {
        Map<String, Object> headers = new HashMap<>(rangeHeaders());
        headers.put(MinioConstants.MINIO_OPERATION, "getPartialObject");
        assertEquals("World", template.requestBodyAndHeaders("direct:header", null, headers, String.class));
    }
}
