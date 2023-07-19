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
package org.apache.camel.generator.openapi;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.apicurio.datamodels.models.openapi.OpenApiOperation;

public class DefaultDestinationGenerator implements DestinationGenerator {

    private final AtomicInteger counter = new AtomicInteger();
    private final String syntax;

    public DefaultDestinationGenerator() {
        this("direct:${operationId}");
    }

    public DefaultDestinationGenerator(String syntax) {
        this.syntax = syntax;
    }

    @Override
    public String generateDestinationFor(final OpenApiOperation operation) {
        String answer = syntax;
        if (answer.contains("${operationId")) {
            String id = Optional.ofNullable(operation.getOperationId()).orElseGet(this::generateDirectName);
            answer = answer.replace("${operationId}", id);
        }
        return answer;
    }

    String generateDirectName() {
        return "rest" + counter.incrementAndGet();
    }

}
