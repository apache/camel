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
 * A <a href="http://activemq.apache.org/camel/processor.html">processor</a>
 * is used to implement the
 * <a href="http://activemq.apache.org/camel/event-driven-consumer.html">Event Driven Consumer</a>
 * and <a href="http://activemq.apache.org/camel/message-transformer.html">Message Transformer</a>
 * patterns and to process message exchanges.
 *
 * @version $Revision$
 */
public interface Processor {

    /**
     * Processes the message exchange
     * 
     * @throws Exception if an internal processing error has occurred. 
     */
    void process(Exchange exchange) throws Exception;
}
