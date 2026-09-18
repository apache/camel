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

import org.apache.camel.spi.Metadata;

/**
 * A <a href="https://camel.apache.org/processor.html">processor</a> is used to implement the
 * <a href="https://camel.apache.org/event-driven-consumer.html"> Event Driven Consumer</a> and
 * <a href="https://camel.apache.org/message-translator.html"> Message Translator</a> patterns and to process message
 * exchanges.
 * <p/>
 * Notice if you use a {@link Processor} in a Camel route, then make sure to write the {@link Processor} in a
 * thread-safe way, as the Camel routes can potentially be executed by concurrent threads, and therefore multiple
 * threads can call the same {@link Processor} instance.
 *
 * @see AsyncProcessor
 * @see Exchange
 */
@FunctionalInterface
@Metadata(label = "api",
          description = "A step written in Java: process(exchange) reads and writes the exchange in place, the result "
                        + "goes into exchange.getMessage(). Used as .process(new MyProcessor()) or a lambda, or as a bean "
                        + "(process: {ref: myProcessor}); a plain bean method taking the body (.bean(myBean, \"method\")) "
                        + "is often simpler than a Processor.")
public interface Processor {

    /**
     * Processes the message exchange
     *
     * @param  exchange  the message exchange
     * @throws Exception if an internal processing error has occurred.
     */
    @Metadata(label = "api",
              important = true,
              description = "Processes the exchange in place: read exchange.getMessage().getBody(type), put the result with "
                            + "setBody; throw to fail the exchange.",
              examples = {
                      "exchange -> "
                           + "exchange.getMessage().setBody(exchange.getMessage().getBody(String.class).toUpperCase())" })
    void process(Exchange exchange) throws Exception;
}
