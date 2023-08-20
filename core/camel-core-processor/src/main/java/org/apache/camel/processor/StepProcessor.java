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
package org.apache.camel.processor;

import java.util.Collection;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.support.EventHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StepProcessor extends Pipeline {

    private static final Logger LOG = LoggerFactory.getLogger(StepProcessor.class);

    private final String stepId;

    public StepProcessor(CamelContext camelContext, Collection<Processor> processors, String stepId) {
        super(camelContext, processors);
        this.stepId = stepId;
    }

    public static Processor newInstance(CamelContext camelContext, List<Processor> processors, String stepId) {
        if (processors.isEmpty()) {
            return null;
        } else if (processors.size() == 1) {
            return processors.get(0);
        }
        return new StepProcessor(camelContext, processors, stepId);
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        // setup step id on exchange
        final Object oldStepId = exchange.removeProperty(ExchangePropertyKey.STEP_ID);
        exchange.setProperty(ExchangePropertyKey.STEP_ID, stepId);

        EventHelper.notifyStepStarted(exchange.getContext(), exchange, stepId);

        return super.process(exchange, sync -> {
            // then fire event to signal the step is done
            boolean failed = exchange.isFailed();
            try {
                if (failed) {
                    EventHelper.notifyStepFailed(exchange.getContext(), exchange, stepId);
                } else {
                    EventHelper.notifyStepDone(exchange.getContext(), exchange, stepId);
                }
            } catch (Exception t) {
                // must catch exceptions to ensure synchronizations is also invoked
                LOG.warn("Exception occurred during event notification. This exception will be ignored.", t);
            } finally {
                if (oldStepId != null) {
                    // restore step id
                    exchange.setProperty(ExchangePropertyKey.STEP_ID, oldStepId);
                } else {
                    // clear step id
                    exchange.removeProperty(ExchangePropertyKey.STEP_ID);
                }
                callback.done(sync);
            }
        });
    }

    @Override
    public String getTraceLabel() {
        return "step";
    }

}
