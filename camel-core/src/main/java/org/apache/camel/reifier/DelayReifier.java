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

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.Delayer;
import org.apache.camel.spi.RouteContext;

class DelayReifier extends ExpressionReifier<DelayDefinition> {

    DelayReifier(ProcessorDefinition<?> definition) {
        super(DelayDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, false);
        Expression delay = createAbsoluteTimeDelayExpression(routeContext);

        boolean async = definition.getAsyncDelayed() != null && definition.getAsyncDelayed();
        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, definition, async);
        ScheduledExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredScheduledExecutorService(routeContext, "Delay", definition, async);

        Delayer answer = new Delayer(routeContext.getCamelContext(), childProcessor, delay, threadPool, shutdownThreadPool);
        if (definition.getAsyncDelayed() != null) {
            answer.setAsyncDelayed(definition.getAsyncDelayed());
        }
        if (definition.getCallerRunsWhenRejected() == null) {
            // should be default true
            answer.setCallerRunsWhenRejected(true);
        } else {
            answer.setCallerRunsWhenRejected(definition.getCallerRunsWhenRejected());
        }
        return answer;
    }

    private Expression createAbsoluteTimeDelayExpression(RouteContext routeContext) {
        ExpressionDefinition expr = definition.getExpression();
        if (expr != null) {
            return expr.createExpression(routeContext);
        }
        return null;
    }

}
