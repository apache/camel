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
package org.apache.camel.service.lra;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.saga.CamelSagaStep;

public final class LRASagaStep {

    private Optional<Endpoint> compensation;

    private Optional<Endpoint> completion;

    private Map<String, String> options;

    private Optional<Long> timeoutInMilliseconds;

    private LRASagaStep() {
    }

    public static LRASagaStep fromCamelSagaStep(CamelSagaStep step, Exchange exchange) {
        LRASagaStep t = new LRASagaStep();
        t.compensation = step.getCompensation();
        t.completion = step.getCompletion();
        t.timeoutInMilliseconds = step.getTimeoutInMilliseconds();
        t.options = new TreeMap<>();
        for (Map.Entry<String, Expression> entry : step.getOptions().entrySet()) {
            try {
                t.options.put(entry.getKey(), entry.getValue().evaluate(exchange, String.class));
            } catch (Exception ex) {
                throw new RuntimeCamelException("Cannot evaluate saga option '" + entry.getKey() + "'", ex);
            }
        }
        return t;
    }

    public Optional<Endpoint> getCompensation() {
        return compensation;
    }

    public Optional<Endpoint> getCompletion() {
        return completion;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public Optional<Long> getTimeoutInMilliseconds() {
        return timeoutInMilliseconds;
    }

    @Override
    public String toString() {
        return "LRASagaStep{"
                + "compensation=" + compensation
                + ", completion=" + completion
                + ", options=" + options
                + ", timeoutInMilliseconds=" + timeoutInMilliseconds
                + '}';
    }
}
