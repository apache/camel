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
package camel.example;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test fixture mirroring {@code src/main/resources/examples/run/order-generator/OrderNumber.java}, which the CLI
 * compiles at runtime, so that {@code examples/run/order-generator/beans.yaml} can instantiate its {@code orderNumber}
 * bean while {@link org.apache.camel.dsl.jbang.core.common.ExampleRoutesLoadTest} pre-parses it.
 *
 * Keep this in sync with the example source (synced from camel-jbang-examples). Only the type and its {@code start}
 * property are load-bearing for the test.
 */
public class OrderNumber {

    private final AtomicInteger counter = new AtomicInteger();

    public void setStart(int start) {
        counter.set(start - 1);
    }

    public String next() {
        return "ORD-" + counter.incrementAndGet();
    }
}
