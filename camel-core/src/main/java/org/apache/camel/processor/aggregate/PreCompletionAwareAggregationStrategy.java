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

import org.apache.camel.Exchange;

public interface PreCompletionAwareAggregationStrategy extends AggregationStrategy {

    /**
     * Determines if the aggregation should complete the current group, and start a new group, or the aggregation
     * should continue using the current group.
     *
     * @param oldExchange the oldest exchange (is <tt>null</tt> on first aggregation as we only have the new exchange)
     * @param newExchange the newest exchange (can be <tt>null</tt> if there was no data possible to acquire)
     * @return <tt>true</tt> to complete current group and start a new group, or <tt>false</tt> to keep using current
     */
    boolean preComplete(Exchange oldExchange, Exchange newExchange);
}
