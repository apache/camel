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

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.processor.WrapProcessor;
import org.apache.camel.spi.Policy;

public class TransactedReifier extends AbstractPolicyReifier<TransactedDefinition> {

    public TransactedReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (TransactedDefinition) definition);
    }

    public TransactedReifier(CamelContext camelContext, ProcessorDefinition<?> definition) {
        super(camelContext, (TransactedDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        Policy policy = resolvePolicy();
        org.apache.camel.util.ObjectHelper.notNull(policy, "policy", this);

        // before wrap
        policy.beforeWrap(route, definition);

        // create processor after the before wrap
        Processor childProcessor = this.createChildProcessor(true);

        // wrap
        Processor target = policy.wrap(route, childProcessor);

        if (!(target instanceof Service)) {
            // wrap the target so it becomes a service and we can manage its
            // lifecycle
            target = new WrapProcessor(target, childProcessor);
        }
        return target;
    }

    public Policy resolvePolicy() {
        return resolvePolicy(definition.getPolicy(), definition.getRef(), definition.getType());
    }

}
