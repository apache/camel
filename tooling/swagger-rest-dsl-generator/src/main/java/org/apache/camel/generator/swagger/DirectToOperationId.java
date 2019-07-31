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
package org.apache.camel.generator.swagger;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.swagger.models.Operation;

public final class DirectToOperationId implements DestinationGenerator {

    private final AtomicInteger directRouteCount = new AtomicInteger(0);

    @Override
    public String generateDestinationFor(final Operation operation) {
        return "direct:" + Optional.ofNullable(operation.getOperationId()).orElseGet(this::generateDirectName);
    }

    String generateDirectName() {
        return "rest" + directRouteCount.incrementAndGet();
    }

}
