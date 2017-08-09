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
package org.apache.camel.saga;

import java.util.Map;
import java.util.Optional;

import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.util.ObjectHelper;

/**
 * Defines the configuration of a saga step.
 */
public class CamelSagaStep {

    private Optional<Endpoint> compensation;

    private Optional<Endpoint> completion;

    private Map<String, Expression> options;

    private Optional<Long> timeoutInMilliseconds;

    public CamelSagaStep(Optional<Endpoint> compensation, Optional<Endpoint> completion, Map<String, Expression> options, Optional<Long> timeoutInMilliseconds) {
        this.compensation = ObjectHelper.notNull(compensation, "compensation");
        this.completion = ObjectHelper.notNull(completion, "completionCallbacks");
        this.options = ObjectHelper.notNull(options, "options");
        this.timeoutInMilliseconds = ObjectHelper.notNull(timeoutInMilliseconds, "timeoutInMilliseconds");
    }

    public Optional<Endpoint> getCompensation() {
        return compensation;
    }

    public Optional<Endpoint> getCompletion() {
        return completion;
    }

    public Map<String, Expression> getOptions() {
        return options;
    }

    public Optional<Long> getTimeoutInMilliseconds() {
        return timeoutInMilliseconds;
    }

    public boolean isEmpty() {
        return !compensation.isPresent() && !completion.isPresent() && options.isEmpty() && !timeoutInMilliseconds.isPresent();
    }
}
