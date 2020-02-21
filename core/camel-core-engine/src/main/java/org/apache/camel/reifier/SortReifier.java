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

import java.util.Comparator;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.processor.SortProcessor;
import org.apache.camel.support.ObjectHelper;

import static org.apache.camel.builder.ExpressionBuilder.bodyExpression;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public class SortReifier<T, U extends SortDefinition<T>> extends ExpressionReifier<U> {

    public SortReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (U) definition);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Processor createProcessor() throws Exception {
        // lookup in registry
        if (isNotEmpty(definition.getComparatorRef())) {
            definition.setComparator(lookup(parseString(definition.getComparatorRef()), Comparator.class));
        }

        // if no comparator then default on to string representation
        if (definition.getComparator() == null) {
            definition.setComparator(new Comparator<T>() {
                public int compare(T o1, T o2) {
                    return ObjectHelper.compare(o1, o2);
                }
            });
        }

        // if no expression provided then default to body expression
        Expression exp;
        if (definition.getExpression() == null) {
            exp = bodyExpression();
        } else {
            exp = createExpression(definition.getExpression());
        }
        return new SortProcessor<T>(exp, definition.getComparator());
    }

}
