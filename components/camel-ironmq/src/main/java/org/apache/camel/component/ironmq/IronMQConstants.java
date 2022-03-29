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
package org.apache.camel.component.ironmq;

import org.apache.camel.spi.Metadata;

public interface IronMQConstants {

    @Metadata(description = "(producer) The id of the IronMQ message as a String when sending a single message, or a Ids object when sending a array of strings."
                            +
                            " (consumer) The id of the message.",
              javaType = "String or io.iron.ironmq.Ids")
    String MESSAGE_ID = "CamelIronMQMessageId";
    @Metadata(label = "consumer", description = "The reservation id of the message.", javaType = "String")
    String MESSAGE_RESERVATION_ID = "CamelIronMQReservationId";
    @Metadata(label = "consumer", description = "The number of times this message has been reserved.", javaType = "long")
    String MESSAGE_RESERVED_COUNT = "CamelIronMQReservedCount";
    @Metadata(label = "producer",
              description = "If value set to 'CamelIronMQClearQueue' the queue is cleared of unconsumed  messages.",
              javaType = "String")
    String OPERATION = "CamelIronMQOperation";
    String CLEARQUEUE = "CamelIronMQClearQueue";

}
