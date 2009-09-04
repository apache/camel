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

import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RedeliveryPolicyDefinition;

/**
 *
 */
public final class OnExceptionDefinitionRenderer {
    private OnExceptionDefinitionRenderer() {
        // Utility class, no public or protected default constructor
    }

    public static void render(StringBuilder buffer, ProcessorDefinition<?> processor) {
        // if not a global onCompletion, add a period
        boolean notGlobal = buffer.toString().endsWith(")");
        if (notGlobal) {
            buffer.append(".");
        }

        OnExceptionDefinition onException = (OnExceptionDefinition)processor;
        buffer.append(processor.getShortName()).append("(");
        List<Class> exceptions = onException.getExceptionClasses();
        for (Class excep : exceptions) {
            buffer.append(excep.getSimpleName()).append(".class");
            if (excep != exceptions.get(exceptions.size() - 1)) {
                buffer.append(", ");
            }
        }
        buffer.append(")");

        // render the redelivery policy
        if (onException.getRedeliveryPolicy() != null) {
            RedeliveryPolicyDefinition redelivery = onException.getRedeliveryPolicy();
            if (redelivery.getMaximumRedeliveries() != null) {
                int maxRediliveries = redelivery.getMaximumRedeliveries();
                if (maxRediliveries != 0) {
                    buffer.append(".maximumRedeliveries(").append(maxRediliveries).append(")");
                }
            }
            if (redelivery.getRedeliveryDelay() != null) {
                long redeliverDelay = redelivery.getRedeliveryDelay();
                if (redeliverDelay != 1000) {
                    buffer.append(".redeliverDelay(").append(redeliverDelay).append(")");
                }
            }
            if (redelivery.getLogStackTrace() != null) {
                if (redelivery.getLogStackTrace()) {
                    buffer.append(".logStackTrace(true)");
                }
            }
        }

        // render the handled policy
        if (onException.getHandledPolicy() != null) {
            String handledPolicy = onException.getHandledPolicy().toString();
            if (handledPolicy.equals("false")) {
                buffer.append(".handled(").append(handledPolicy).append(")");
            }
        }

        List<ProcessorDefinition> branches = onException.getOutputs();
        for (ProcessorDefinition branch : branches) {
            SendDefinitionRenderer.render(buffer, branch);
        }
    }
}
