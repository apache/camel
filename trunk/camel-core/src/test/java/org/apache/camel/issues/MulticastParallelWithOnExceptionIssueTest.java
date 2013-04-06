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
package org.apache.camel.issues;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class MulticastParallelWithOnExceptionIssueTest extends MulticastWithOnExceptionIssueTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .handled(true)
                    .to("log:onException")
                    .to("mock:end4")    
                    .transform(constant("Stop!"));

                from("direct:start")
                    .multicast().parallelProcessing()
                        .to("mock:end1", "mock:end2")
                    .end()
                    .to("mock:end3")
                    .transform(constant("Hello to you too!"));
            }
        };
    }
}
