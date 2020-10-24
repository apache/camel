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
package org.apache.camel.reifier;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.Delayer;

public class DelayReifier extends ExpressionReifier<DelayDefinition> {

    public DelayReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, DelayDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        Processor childProcessor = this.createChildProcessor(false);
        Expression delay = createAbsoluteTimeDelayExpression();

        boolean async = parseBoolean(definition.getAsyncDelayed(), true);
        boolean shutdownThreadPool = willCreateNewThreadPool(definition, async);
        ScheduledExecutorService threadPool = getConfiguredScheduledExecutorService("Delay", definition, async);

        Delayer answer = new Delayer(camelContext, childProcessor, delay, threadPool, shutdownThreadPool);
        answer.setAsyncDelayed(async);
        answer.setCallerRunsWhenRejected(parseBoolean(definition.getCallerRunsWhenRejected(), true));
        return answer;
    }

    private Expression createAbsoluteTimeDelayExpression() {
        return definition.getExpression() != null ? createExpression(definition.getExpression()) : null;
    }

}
