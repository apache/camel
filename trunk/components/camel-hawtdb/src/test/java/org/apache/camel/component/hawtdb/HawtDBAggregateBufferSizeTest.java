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
package org.apache.camel.component.hawtdb;

import org.apache.camel.builder.RouteBuilder;

public class HawtDBAggregateBufferSizeTest extends HawtDBAggregateTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                HawtDBAggregationRepository repo = new HawtDBAggregationRepository("repo1", "target/data/hawtdb.dat");
                // use 2mb file
                repo.setBufferSize(1024 * 1024 * 2);

                // here is the Camel route where we aggregate
                from("direct:start")
                    .aggregate(header("id"), new MyAggregationStrategy())
                        .completionSize(5).aggregationRepository(repo)
                        .to("mock:aggregated");
            }
        };
    }

}