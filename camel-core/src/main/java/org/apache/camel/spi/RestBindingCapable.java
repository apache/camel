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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.restbinding.RestBindingEndpoint;

public interface RestBindingCapable {

    /**
     * Creates a new REST <a
     * href="http://camel.apache.org/event-driven-consumer.html">Event
     * Driven Consumer</a>, using the details from the {@link org.apache.camel.component.restbinding.RestBindingEndpoint},
     * which consumes messages from the endpoint using the given processor
     *
     * @param endpoint  the binding endpoint
     * @param processor the processor
     * @return a newly created REST consumer
     * @throws Exception can be thrown
     */
    Consumer createConsumer(RestBindingEndpoint endpoint, Processor processor) throws Exception;
}
