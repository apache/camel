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

import org.apache.camel.NonManagedService;
import org.apache.camel.ShutdownableService;
import org.apache.camel.StaticService;

/**
 * A shared {@link org.apache.camel.impl.DefaultProducerServicePool} which is used by
 * {@link org.apache.camel.CamelContext} by default.
 *
 * @version 
 */
@Deprecated
public class SharedProducerServicePool extends DefaultProducerServicePool implements ShutdownableService, StaticService, NonManagedService {

    public SharedProducerServicePool() {
    }

    public SharedProducerServicePool(int capacity) {
        super(capacity);
    }

    @Override
    protected void doStop() throws Exception {
        // only let CamelContext stop it since its shared and should
        // only be stopped when CamelContext stops
    }

    @Override
    protected void doShutdown() throws Exception {
        // now we are shutting down then stop it, which properly stops the pool
        super.doStop();
    }

}
