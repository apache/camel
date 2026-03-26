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
import java.util.Random;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bulk.BulkException;

//
// Bulk error recovery example.
//
// Demonstrates:
// - Error threshold to abort when too many items fail
// - FAILURES_ONLY accept policy for a recovery step
// - doTry/doCatch for handling bulk abort
//
// Run with:
//   camel run batch-error-recovery.java
//
public class batcherrorrecovery extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        // Generate items and process with error recovery
        from("timer:recovery?period=8000")
                .process(e -> {
                    List<String> items = new ArrayList<>();
                    for (int i = 1; i <= 15; i++) {
                        items.add("item-" + i);
                    }
                    e.getIn().setBody(items);
                })
                .log("Processing ${body.size()} items with error recovery...")
                .doTry()
                    .to("bulk:recoveryJob?steps=direct:primary,direct:recover"
                            + "&acceptPolicy=FAILURES_ONLY&errorThreshold=0.5")
                    .log("Batch succeeded: ${body}")
                .doCatch(BulkException.class)
                    .log("Batch aborted! ${exception.message}")
                .end();

        // Primary step: randomly fails ~30% of items
        from("direct:primary")
                .process(e -> {
                    int index = e.getIn().getHeader("CamelBulkIndex", Integer.class);
                    if (index % 3 == 0) {
                        throw new RuntimeException("Primary processing failed for " + e.getIn().getBody());
                    }
                })
                .log("  [primary] OK: ${body}");

        // Recovery step: only receives items that failed in primary
        from("direct:recover")
                .log("  [recover] Retrying failed item ${header.CamelBulkIndex}: ${body}")
                .process(e -> {
                    // Recovery logic — always succeeds
                    e.getIn().setHeader("recovered", true);
                })
                .log("  [recover] Recovered: ${body}");
    }
}
