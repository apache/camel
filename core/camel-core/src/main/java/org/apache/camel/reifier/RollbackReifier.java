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
package org.apache.camel.reifier;

import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RollbackDefinition;
import org.apache.camel.processor.RollbackProcessor;
import org.apache.camel.spi.RouteContext;

class RollbackReifier extends ProcessorReifier<RollbackDefinition> {

    RollbackReifier(ProcessorDefinition<?> definition) {
        super((RollbackDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        boolean isMarkRollbackOnly = definition.getMarkRollbackOnly() != null && definition.getMarkRollbackOnly();
        boolean isMarkRollbackOnlyLast = definition.getMarkRollbackOnlyLast() != null && definition.getMarkRollbackOnlyLast();

        // validate that only either mark rollbacks is chosen and not both
        if (isMarkRollbackOnly && isMarkRollbackOnlyLast) {
            throw new IllegalArgumentException("Only either one of markRollbackOnly and markRollbackOnlyLast is possible to select as true");
        }

        RollbackProcessor answer = new RollbackProcessor(definition.getMessage());
        answer.setMarkRollbackOnly(isMarkRollbackOnly);
        answer.setMarkRollbackOnlyLast(isMarkRollbackOnlyLast);
        return answer;
    }

}
