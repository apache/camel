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

import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.WhenDefinition;

/**
 *
 */
public final class OnCompletionDefinitionRenderer {
    private OnCompletionDefinitionRenderer() {
        // Utility class, no public or protected default constructor
    }    

    public static void render(StringBuilder buffer, ProcessorDefinition processor) {
        // if not a global onCompletion, add a period
        boolean notGlobal = buffer.toString().endsWith(")");
        if (notGlobal) {
            buffer.append(".");
        }

        OnCompletionDefinition onComplete = (OnCompletionDefinition)processor;
        buffer.append(processor.getShortName()).append("()");
        if (onComplete.getOnWhen() != null) {
            WhenDefinition when = onComplete.getOnWhen();
            buffer.append(".onWhen");
            if (when.getExpression().getPredicate() != null) {
                buffer.append("(");
                PredicateRenderer.render(buffer, when.getExpression().getPredicate());
                buffer.append(")");
            } else {
                buffer.append("Unsupported Expression!");
            }
        }
        if (onComplete.getOnCompleteOnly()) {
            buffer.append(".onCompleteOnly()");
        }
        if (onComplete.getOnFailureOnly()) {
            buffer.append(".onFailureOnly()");
        }
        List<ProcessorDefinition> branches = onComplete.getOutputs();
        for (ProcessorDefinition branch : branches) {
            SendDefinitionRenderer.render(buffer, branch);
        }

        // if not a global onCompletion, using end() at the end
        if (notGlobal) {
            buffer.append(".end()");
        }
    }
}
