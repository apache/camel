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
package org.apache.camel.component.activemq.converter;

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.camel.Converter;

@Converter(generateLoader = true)
public class ActiveMQConverter {

    /**
     * Converts a URL in ActiveMQ syntax to a destination such as to support
     * "queue://foo.bar" or 'topic://bar.whatnot". Things default to queues if
     * no scheme. This allows ActiveMQ destinations to be passed around as
     * Strings and converted back again.
     *
     * @param name is the name of the queue or the full URI using prefixes
     *            queue:// or topic://
     * @return the ActiveMQ destination
     */
    @Converter
    public ActiveMQDestination toDestination(String name) {
        return ActiveMQDestination.createDestination(name, ActiveMQDestination.QUEUE_TYPE);
    }

}
