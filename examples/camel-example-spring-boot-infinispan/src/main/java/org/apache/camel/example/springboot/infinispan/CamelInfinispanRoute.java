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
package org.apache.camel.example.springboot.infinispan;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.springframework.stereotype.Component;

/**
 * A simple Camel Infinispan route example using Spring-boot
 */
@Component
public class CamelInfinispanRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("timer://foo?period=10000&repeatCount=1")
        .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.PUT)
        .setHeader(InfinispanConstants.KEY).constant("1")
        .setHeader(InfinispanConstants.VALUE).constant("test")
        .to("infinispan://default")
        .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GET)
        .setHeader(InfinispanConstants.KEY).constant("1")
        .to("infinispan://default").log("Received body: ${body}");
    }

}
