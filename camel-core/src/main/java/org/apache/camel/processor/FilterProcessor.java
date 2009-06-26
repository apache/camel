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

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The processor which implements the
 * <a href="http://camel.apache.org/message-filter.html">Message Filter</a> EIP pattern.
 *
 * @version $Revision$
 */
public class FilterProcessor extends DelegateProcessor implements Traceable {
    private static final Log LOG = LogFactory.getLog(FilterProcessor.class);
    private final Predicate predicate;

    public FilterProcessor(Predicate predicate, Processor processor) {
        super(processor);
        this.predicate = predicate;
    }

    public void process(Exchange exchange) throws Exception {
        if (predicate.matches(exchange)) {
            super.process(exchange);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Marking exchange as filtered: " + exchange);
            }
            // mark this exchange as filtered
            exchange.setProperty(Exchange.FILTERED, Boolean.TRUE);
        }
    }

    @Override
    public String toString() {
        return "Filter[if: " + predicate + " do: " + getProcessor() + "]";
    }

    public String getTraceLabel() {
        return "Filter[if: " + predicate + "]";
    }

    public Predicate getPredicate() {
        return predicate;
    }
}
