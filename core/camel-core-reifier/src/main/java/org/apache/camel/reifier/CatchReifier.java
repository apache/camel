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

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.processor.CatchProcessor;

public class CatchReifier extends ProcessorReifier<CatchDefinition> {

    public CatchReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, CatchDefinition.class.cast(definition));
    }

    @Override
    public CatchProcessor createProcessor() throws Exception {
        // create and load exceptions if not done
        if (definition.getExceptionClasses() == null) {
            definition.setExceptionClasses(createExceptionClasses());
        }

        // must have at least one exception
        if (definition.getExceptionClasses().isEmpty()) {
            throw new IllegalArgumentException("At least one Exception must be configured to catch");
        }

        // parent must be a try
        if (!(definition.getParent() instanceof TryDefinition)) {
            throw new IllegalArgumentException("This doCatch should have a doTry as its parent on " + definition);
        }

        // do catch does not mandate a child processor
        Processor childProcessor = this.createChildProcessor(false);

        Predicate when = null;
        if (definition.getOnWhen() != null) {
            when = createPredicate(definition.getOnWhen().getExpression());
        }

        return new CatchProcessor(definition.getExceptionClasses(), childProcessor, when, null);
    }

    protected List<Class<? extends Throwable>> createExceptionClasses() throws ClassNotFoundException {
        // must use the class resolver from CamelContext to load classes to
        // ensure it can
        // be loaded in all kind of environments such as JEE servers and OSGi
        // etc.
        List<String> list = definition.getExceptions();
        List<Class<? extends Throwable>> answer = new ArrayList<>(list.size());
        for (String name : list) {
            Class<Throwable> type = camelContext.getClassResolver().resolveMandatoryClass(name, Throwable.class);
            answer.add(type);
        }
        return answer;
    }
}
