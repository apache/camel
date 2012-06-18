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

/**
 * A specialized {@link AggregationStrategy} which has callback when the aggregated {@link Exchange} is completed.
 *
 * @version
 */
public interface CompletionAwareAggregationStrategy extends AggregationStrategy {

    // TODO: In Camel 3.0 we should move this to org.apache.camel package

    /**
     * The aggregated {@link Exchange} has completed
     *
     * <b>Important: </b> This method must <b>not</b> throw any exceptions.
     *
     * @param exchange  the current aggregated exchange, or the original {@link org.apache.camel.Exchange} if no aggregation
     *                  has been done before the completion occurred
     */
    void onCompletion(Exchange exchange);
}
