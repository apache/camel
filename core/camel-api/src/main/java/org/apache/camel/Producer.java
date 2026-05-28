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
package org.apache.camel;

/**
 * A service that sends {@link Exchange}s to an {@link Endpoint}.
 * <p/>
 * A producer is created by calling {@link Endpoint#createProducer()} and participates in the Camel lifecycle as a
 * {@link Service}. Calls to {@link #process(Exchange)} block until processing of the exchange completes; for
 * non-blocking dispatch use {@link AsyncProducer}.
 * <p/>
 * Important: Do not do any initialization in the constructor. Instead use
 * {@link org.apache.camel.support.service.ServiceSupport#doInit()} or
 * {@link org.apache.camel.support.service.ServiceSupport#doStart()}.
 *
 * @see Consumer
 * @see AsyncProducer
 * @see Endpoint
 */
public interface Producer extends Processor, Service, IsSingleton, EndpointAware {

}
