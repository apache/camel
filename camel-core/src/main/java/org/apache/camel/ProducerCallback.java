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
package org.apache.camel;

/**
 * Callback for sending a exchange message to a endpoint using a producer.
 * <p/>
 * Using this callback as a template pattern ensures that Camel handles the resource handling and will
 * start and stop the given producer, to avoid resource leaks.
 *
 */
public interface ProducerCallback<T> {

    /**
     * Performs operation on the given producer to send the given exchange.
     *
     * @param producer        the producer, is never <tt>null</tt>
     * @param exchange        the exchange, can be <tt>null</tt> if so then create a new exchange from the producer
     * @param exchangePattern the exchange pattern, can be <tt>null</tt>
     * @return the response
     * @throws Exception if an internal processing error has occurred.
     */
    T doInProducer(Producer producer, Exchange exchange, ExchangePattern exchangePattern) throws Exception;
}
