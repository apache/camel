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

import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ProcessorDefinition;

/**
 * 
 */
public final class CatchDefinitionRenderer {
    private CatchDefinitionRenderer() {
        // Utility class, no public or protected default constructor
    }
    
    public static void render(StringBuilder buffer, ProcessorDefinition<?> processor) {
        CatchDefinition catchDef = (CatchDefinition)processor;
        buffer.append(".").append(catchDef.getShortName()).append("(");
        List<Class> exceptions = catchDef.getExceptionClasses();
        for (Class clazz : exceptions) {
            buffer.append(clazz.getSimpleName()).append(".class");
            if (clazz != exceptions.get(exceptions.size() - 1)) {
                buffer.append(", ");
            }
        }
        buffer.append(")");

        // render handled() dsl
        if (catchDef.getHandledPolicy() != null) {
            String handled = catchDef.getHandledPolicy().toString();
            buffer.append(".handled(").append(handled).append(")");
        }

        List<ProcessorDefinition> branches = catchDef.getOutputs();
        for (ProcessorDefinition branch : branches) {
            SendDefinitionRenderer.render(buffer, branch);
        }
    }
}
