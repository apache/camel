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
package org.apache.camel.impl.engine;

import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.processor.EventNotifierProducer;
import org.apache.camel.processor.UnitOfWorkProducer;
import org.apache.camel.spi.DeferServiceFactory;

public final class DefaultDeferServiceFactory implements DeferServiceFactory {
    
    @Override
    public Producer createProducer(Endpoint endpoint) throws Exception {
        Producer producer = new DeferProducer(endpoint);
        producer = new UnitOfWorkProducer(producer);
        producer = new EventNotifierProducer(producer);
        endpoint.getCamelContext().deferStartService(producer, true);
        return producer;
    }

}
