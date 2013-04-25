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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;

/**
 * A base class for an endpoint which the default consumer mode is to use a {@link org.apache.camel.PollingConsumer}
 *
 * @version 
 */
public abstract class DefaultPollingEndpoint extends ScheduledPollEndpoint  {

    protected DefaultPollingEndpoint() {
    }

    protected DefaultPollingEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Deprecated
    protected DefaultPollingEndpoint(String endpointUri) {
        super(endpointUri);
    }

    @Deprecated
    protected DefaultPollingEndpoint(String endpointUri, CamelContext context) {
        super(endpointUri, context);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer result = new DefaultScheduledPollConsumer(this, processor);
        configureConsumer(result);
        return result;
    }
}
