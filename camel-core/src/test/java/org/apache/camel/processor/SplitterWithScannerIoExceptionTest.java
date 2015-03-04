/**
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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class SplitterWithScannerIoExceptionTest extends ContextTestSupport {

    public void testSplitterStreamingWithError() throws Exception {
        if (isPlatform("aix") || isJavaVendor("ibm")) {
            return;
        }

        getMockEndpoint("mock:a").expectedMinimumMessageCount(250);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:b").setSleepForEmptyTest(3000);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error"));
                
                // wrong encoding to force the scanner to fail
                from("file://src/test/data?fileName=crm.sample.csv&noop=true&charset=UTF-8")
                    .split(body().tokenize("\n")).streaming()
                        .to("mock:a")
                    .end()
                    .to("mock:b");
            }
        };
    }
}