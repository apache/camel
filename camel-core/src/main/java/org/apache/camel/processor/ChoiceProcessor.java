/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements a Choice structure where one or more predicates are used which if they are true their processors
 * are used, with a default otherwise clause used if none match.
 *
 * @version $Revision$
 */
public class ChoiceProcessor<E> implements Processor<E> {
    private List<FilterProcessor<E>> filters = new ArrayList<FilterProcessor<E>>();
    private Processor<E> otherwise;

    public ChoiceProcessor(List<FilterProcessor<E>> filters, Processor<E> otherwise) {
        this.filters = filters;
        this.otherwise = otherwise;
    }

    public void onExchange(E exchange) {
        for (FilterProcessor<E> filterProcessor : filters) {
            Predicate<E> predicate = filterProcessor.getPredicate();
            if (predicate != null) {
                filterProcessor.getProcessor().onExchange(exchange);
                return;
            }
        }
        if (otherwise != null) {
            otherwise.onExchange(exchange);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("choice{");
        boolean first = true;
        for (FilterProcessor<E> processor : filters) {
            if (first) {
                first = false;
            }
            else {
                builder.append(", ");
            }
            builder.append("when ");
            builder.append(processor.getPredicate().toString());
            builder.append(": ");
            builder.append(processor.getProcessor());
        }
        if (otherwise != null) {
            builder.append(", otherwise: ");
            builder.append(otherwise);
        }
        builder.append("}");
        return builder.toString();
    }

    public List<FilterProcessor<E>> getFilters() {
        return filters;
    }

    public Processor<E> getOtherwise() {
        return otherwise;
    }
}
