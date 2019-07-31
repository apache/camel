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
package org.apache.camel.component.spring.ws.filter;

import org.apache.camel.Exchange;
import org.springframework.ws.WebServiceMessage;

/**
 * A strategy instance that filters a WebServiceMessage response.
 * 
 * This class provides an additional configuration that can be managed in your Spring's context.
 */
public interface MessageFilter {

    /**
     * Calls filter for a producer
     *
     * @param exchange the exchange
     * @param response provided by the producer
     */
    void filterProducer(Exchange exchange, WebServiceMessage response);

    /**
     * Calls filter for a consumer
     * 
     * @param exchange the exchange
     * @param response provided by the consumer
     */
    void filterConsumer(Exchange exchange, WebServiceMessage response);

}
