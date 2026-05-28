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
 * Marker interface indicating that the {@link Endpoint}'s consumer and producer support asynchronous, non-blocking
 * routing.
 * <p/>
 * When an endpoint implements this interface, the Camel routing engine can pass an {@link AsyncCallback} to the
 * producer's {@link AsyncProcessor#process(Exchange, AsyncCallback)} method instead of blocking until the exchange
 * completes.
 *
 * @see Endpoint
 * @see AsyncProcessor
 * @see AsyncProducer
 */
public interface AsyncEndpoint extends Endpoint {

}
