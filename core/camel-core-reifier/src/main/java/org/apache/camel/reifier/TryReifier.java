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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.processor.TryProcessor;

public class TryReifier extends ProcessorReifier<TryDefinition> {

    public TryReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (TryDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        Processor tryProcessor = createOutputsProcessor(definition.getOutputsWithoutCatches());
        if (tryProcessor == null) {
            throw new IllegalArgumentException("Definition has no children on " + this);
        }

        List<Processor> catchProcessors = new ArrayList<>();
        if (definition.getCatchClauses() != null) {
            for (CatchDefinition catchClause : definition.getCatchClauses()) {
                catchProcessors.add(createProcessor(catchClause));
            }
        }

        FinallyDefinition finallyDefinition = definition.getFinallyClause();
        if (finallyDefinition == null) {
            finallyDefinition = new FinallyDefinition();
            finallyDefinition.setParent(definition);
        }
        Processor finallyProcessor = createProcessor(finallyDefinition);

        // must have either a catch or finally
        if (definition.getFinallyClause() == null && definition.getCatchClauses() == null) {
            throw new IllegalArgumentException("doTry must have one or more catch or finally blocks on " + this);
        }

        return new TryProcessor(camelContext, tryProcessor, catchProcessors, finallyProcessor);
    }

}
