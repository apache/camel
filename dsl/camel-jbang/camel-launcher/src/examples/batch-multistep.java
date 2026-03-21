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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;

//
// Multi-step batch processing example.
//
// Demonstrates a batch ETL pipeline with validation, transformation,
// and loading steps. Items that fail validation are skipped in
// subsequent steps using the NO_FAILURES accept policy.
//
// Run with:
//   camel run batch-multistep.java
//
public class batchmultistep extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        // Generate sample records every 10 seconds
        from("timer:etl?period=10000")
                .process(e -> {
                    List<Map<String, Object>> records = new ArrayList<>();
                    for (int i = 1; i <= 10; i++) {
                        Map<String, Object> record = new HashMap<>();
                        record.put("id", i);
                        record.put("name", "Product-" + i);
                        record.put("price", i * 10.0 + (i % 3 == 0 ? -999 : 0)); // negative price for every 3rd item
                        records.add(record);
                    }
                    e.getIn().setBody(records);
                })
                .log("Starting ETL batch with ${body.size()} records...")
                .to("batch:etlJob?steps=direct:validate,direct:transform,direct:load"
                        + "&acceptPolicy=NO_FAILURES&chunkSize=5")
                .log("ETL complete: ${body}");

        // Step 1: Validate — reject records with negative prices
        from("direct:validate")
                .process(e -> {
                    Map<String, Object> record = e.getIn().getBody(Map.class);
                    double price = (double) record.get("price");
                    if (price < 0) {
                        throw new IllegalArgumentException(
                                "Invalid price " + price + " for product " + record.get("name"));
                    }
                })
                .log("  [validate] Record ${header.CamelBatchIndex} OK: ${body}");

        // Step 2: Transform — apply a discount
        from("direct:transform")
                .process(e -> {
                    Map<String, Object> record = e.getIn().getBody(Map.class);
                    double price = (double) record.get("price");
                    record.put("discountedPrice", price * 0.9);
                    record.put("status", "transformed");
                })
                .log("  [transform] Record ${header.CamelBatchIndex}: ${body}");

        // Step 3: Load — simulate persisting
        from("direct:load")
                .log("  [load] Saving record ${header.CamelBatchIndex}: ${body}");
    }
}
