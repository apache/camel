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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * Implements a Choice structure where one or more predicates are used which if
 * they are true their processors are used, with a default otherwise clause used
 * if none match.
 * 
 * @version $Revision$
 */
public class ChoiceProcessor extends ServiceSupport implements Processor {
    private List<FilterProcessor> filters = new ArrayList<FilterProcessor>();
    private Processor otherwise;

    public ChoiceProcessor(List<FilterProcessor> filters, Processor otherwise) {
        this.filters = filters;
        this.otherwise = otherwise;
    }

    public void process(Exchange exchange) throws Exception {
        for (FilterProcessor filterProcessor : filters) {
            Predicate<Exchange> predicate = filterProcessor.getPredicate();
            if (predicate != null && predicate.matches(exchange)) {
                // process next will also take care (has not null test) if next was a stop().
                // stop() has no processor to execute, and thus we will end in a NPE 
                filterProcessor.processNext(exchange);
                return;
            }
        }
        if (otherwise != null) {
            otherwise.process(exchange);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("choice{");
        boolean first = true;
        for (FilterProcessor processor : filters) {
            if (first) {
                first = false;
            } else {
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

    public List<FilterProcessor> getFilters() {
        return filters;
    }

    public Processor getOtherwise() {
        return otherwise;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(filters);
        ServiceHelper.startServices(otherwise);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(otherwise);
        ServiceHelper.stopServices(filters);
    }
}
