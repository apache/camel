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
package org.apache.camel.processor.validation;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A processor which validates the content of the inbound message body against a {@link Predicate}.
 * 
 * @version 
 */
public class PredicateValidatingProcessor extends ServiceSupport implements Processor, Traceable {
    
    private static final Logger LOG = LoggerFactory.getLogger(PredicateValidatingProcessor.class);

    private final Predicate predicate;
    
    public PredicateValidatingProcessor(Predicate predicate) {
        ObjectHelper.notNull(predicate, "predicate", this);
        this.predicate = predicate;
    }

    public void process(Exchange exchange) throws Exception {
        boolean matches = predicate.matches(exchange);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Validation {} for {} with Predicate[{}]", new Object[]{matches ? "succeed" : "failed", exchange, predicate});
        }

        if (!matches) {
            throw new PredicateValidationException(exchange, predicate);
        }
    }

    public Predicate getPredicate() {
        return predicate;
    }

    @Override
    public String toString() {
        return "validate(" + predicate + ")";
    }

    public String getTraceLabel() {
        return "validate[" + predicate + "]";
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
