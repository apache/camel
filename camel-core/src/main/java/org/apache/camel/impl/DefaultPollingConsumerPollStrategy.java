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
package org.apache.camel.impl;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default implementation that will not retry on rollback.
 *
 * @version 
 */
public class DefaultPollingConsumerPollStrategy implements PollingConsumerPollStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public boolean begin(Consumer consumer, Endpoint endpoint) {
        return true;
    }

    public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
        // noop
    }

    public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) throws Exception {
        // we do not want to retry
        return false;
    }

}
