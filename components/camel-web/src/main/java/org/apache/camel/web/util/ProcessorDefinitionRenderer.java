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

import java.util.List;

import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.OutputDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.RollbackDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.ThrottleDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;

/**
 *
 */
public final class ProcessorDefinitionRenderer {
    private ProcessorDefinitionRenderer() {
        // Utility class, no public or protected default constructor
    }

    public static void render(StringBuilder buffer, ProcessorDefinition<?> processor) {
        if (processor instanceof AggregateDefinition) {
            AggregateDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof CatchDefinition) {
            CatchDefinitionRenderer.render(buffer, processor);
            return;
        } else if (processor instanceof ChoiceDefinition) {
            ChoiceDefinitionRenderer.render(buffer, processor);
            return;
        } else if (processor instanceof ConvertBodyDefinition) {
            ConvertBodyDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof ExpressionNode) {
            ExpressionNodeRenderer.render(buffer, processor);
        } else if (processor instanceof LoadBalanceDefinition) {
            LoadBalanceDefinitionRenderer.render(buffer, processor);
            return;
        } else if (processor instanceof OnCompletionDefinition) {
            OnCompletionDefinitionRenderer.render(buffer, processor);
            return;
        } else if (processor instanceof OnExceptionDefinition) {
            OnExceptionDefinitionRenderer.render(buffer, processor);
            return;
        } else if (processor instanceof OutputDefinition) {
            OutputDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof ResequenceDefinition) {
            ResequenceDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof RollbackDefinition) {
            RollbackDefinition rollback = (RollbackDefinition)processor;
            buffer.append(".").append(processor.getShortName()).append("(\"");
            buffer.append(rollback.getMessage()).append("\")");
        } else if (processor instanceof RoutingSlipDefinition) {
            RoutingSlipDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof SendDefinition) {
            SendDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof ThrottleDefinition) {
            ThrottleDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof ThrowExceptionDefinition) {
            ThrowExceptionDefinitionRenderer.render(buffer, processor);
        } else {
            buffer.append(".").append(processor.getShortName()).append("()");
        }

        if (processor instanceof OutputDefinition) {
            OutputDefinition out = (OutputDefinition)processor;
            if (out instanceof FinallyDefinition) {
                return;
            }
        }
        List<ProcessorDefinition> outputs = processor.getOutputs();
        for (ProcessorDefinition nextProcessor : outputs) {
            render(buffer, nextProcessor);
        }
    }
}
