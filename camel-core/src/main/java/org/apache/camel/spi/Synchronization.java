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
package org.apache.camel.spi;

import org.apache.camel.Exchange;

/**
 * Provides a hook for custom {@link org.apache.camel.Processor} or {@link org.apache.camel.Component}
 * instances to respond to completed or failed processing of an {@link Exchange} rather like Spring's
 * <a href="http://static.springframework.org/spring/docs/2.5.x/api/org/springframework/transaction/
 * support/TransactionSynchronization.html">TransactionSynchronization</a>
 *
 * @version 
 */
public interface Synchronization {

    /**
     * Called when the processing of the message exchange is complete
     *
     * @param exchange the exchange being processed
     */
    void onComplete(Exchange exchange);

    /**
     * Called when the processing of the message exchange has failed for some reason.
     * The exception which caused the problem is in {@link Exchange#getException()} and
     * there could be a fault message via {@link org.apache.camel.Message#isFault()}
     *
     * @param exchange the exchange being processed
     */
    void onFailure(Exchange exchange);
}
