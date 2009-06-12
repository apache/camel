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

import java.util.List;

import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test to verify that Splitter aggregator does not included filtered exchanges.
 *
 * @version $Revision$
 */
public class SplitWithNestedFilterShouldSkipFilteredExchanges extends SplitShouldSkipFilteredExchanges {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Predicate goodWord = body().contains("World");

                from("direct:start")
                    .split(body(List.class), new MyAggregationStrategy())
                        .to("mock:split")
                        .filter(goodWord)
                            .to("mock:filtered")
                        .end()
                    .end()
                .to("mock:result");
            }
        };
    }

}