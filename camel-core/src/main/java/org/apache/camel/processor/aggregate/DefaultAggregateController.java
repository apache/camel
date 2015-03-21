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
package org.apache.camel.processor.aggregate;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.support.ServiceSupport;

/**
 * A default {@link org.apache.camel.processor.aggregate.AggregateController} that offers Java and JMX API.
 */
@ManagedResource(description = "Aggregation controller")
public class DefaultAggregateController extends ServiceSupport implements AggregateController {

    private AggregateProcessor processor;
    private String id;

    public void onStart(String id, AggregateProcessor processor) {
        this.id = id;
        this.processor = processor;
    }

    public void onStop(String id, AggregateProcessor processor) {
        this.id = id;
        this.processor = null;
    }

    @ManagedOperation(description = "To force completion a group on the aggregator")
    public int forceCompletionOfGroup(String key) {
        if (processor != null) {
            return processor.forceCompletionOfGroup(key);
        } else {
            return 0;
        }
    }

    @ManagedOperation(description = "To force completion all groups on the aggregator")
    public int forceCompletionOfAllGroups() {
        if (processor != null) {
            return processor.forceCompletionOfAllGroups();
        } else {
            return 0;
        }
    }

    protected void doStart() throws Exception {
        // noop
    }

    protected void doStop() throws Exception {
        // noop
    }

    public String toString() {
        return "DefaultAggregateController[" + id + "]";
    }
}
