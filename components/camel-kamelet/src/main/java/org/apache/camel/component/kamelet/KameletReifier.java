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
package org.apache.camel.component.kamelet;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.support.PluginHelper;

public class KameletReifier extends ProcessorReifier<KameletDefinition> {

    public KameletReifier(Route route, KameletDefinition definition) {
        super(route, definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        Processor processor = createChildProcessor(false);
        if (processor == null) {
            // use an empty noop processor, as there should be a single processor
            processor = new NoopProcessor();
        }
        // wrap in uow
        Processor target = new KameletProcessor(camelContext, parseString(definition.getName()), processor);
        target = PluginHelper.getInternalProcessorFactory(camelContext)
                .addUnitOfWorkProcessorAdvice(camelContext, target, null);
        return target;
    }
}
