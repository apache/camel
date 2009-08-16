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

package org.apache.camel.web.util;

import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.ProcessorDefinition;

/**
 *
 */
public final class AggregateDefinitionRenderer {
    private AggregateDefinitionRenderer() {
        // Utility class, no public or protected default constructor
    }    

    public static void render(StringBuilder buffer, ProcessorDefinition<?> processor) {
        
        AggregateDefinition aggregate = (AggregateDefinition)processor;
        buffer.append(".").append(aggregate.getShortName()).append("()");

        ExpressionRenderer.render(buffer, aggregate.getExpression());

        if (aggregate.getBatchTimeout() != null) {
            buffer.append(".batchTimeout(").append(aggregate.getBatchTimeout());
            buffer.append(aggregate.getBatchTimeout() < 1000L ? "L)" : ")");
        }
        if (aggregate.getBatchSize() != null) {
            buffer.append(".batchSize(").append(aggregate.getBatchSize()).append(")");
        }

        if (aggregate.getGroupExchanges() != null && aggregate.getGroupExchanges()) {
            buffer.append(".groupExchanges()");
        }
    }
}
