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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;

//
// Bulk processing example.
//
// Demonstrates processing a collection of items in structured batches
// with chunking, error handling, and result reporting.
//
// Run with:
//   camel run batch.java
//
public class batch extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        // Generate a list of 20 items every 5 seconds
        from("timer:batch?period=5000")
                .process(e -> {
                    List<String> items = new ArrayList<>();
                    for (int i = 1; i <= 20; i++) {
                        items.add("order-" + i);
                    }
                    e.getIn().setBody(items);
                })
                .log("Sending ${body.size()} items for bulk processing...")
                .to("bulk:orderJob?chunkSize=5&processorRef=direct:processOrder")
                .log("Bulk result: ${body}");

        // Process each individual item
        from("direct:processOrder")
                .log("  Processing item ${header.CamelBulkIndex}/${header.CamelBulkSize}: ${body}")
                .delay(100);
    }
}
