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
package org.apache.camel.component.amqp;

import org.apache.camel.Category;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;

/**
 * Messaging with AMQP protocol using Apache Qpid Client.
 *
 * This class extends JmsEndpoint because it uses Apache Qpid JMS-compatible client for performing the AMQP
 * connectivity.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "amqp", extendsScheme = "jms", title = "AMQP",
             syntax = "amqp:destinationType:destinationName", category = { Category.MESSAGING })
@BodyTypes(consumerOut = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.MAP, Carrier.STREAM, Carrier.NULL },
                                   dependsOn = { "messageConverter", "transferExchange" },
                                   byOption = {
                                           @BodyType.When(option = "mapJmsMessage", value = "false", carriers = Carrier.POJO,
                                                          javaType = "jakarta.jms.Message"),
                                           @BodyType.When(option = "objectMessageEnabled", value = "true",
                                                          carriers = {
                                                                  Carrier.TEXT, Carrier.BYTES, Carrier.MAP,
                                                                  Carrier.STREAM, Carrier.POJO, Carrier.NULL }) },
                                   description = "By the JMS message received: a String for a TextMessage, a byte array for a"
                                                 + " BytesMessage, a Map for a MapMessage, a stream for a StreamMessage;"
                                                 + " an ObjectMessage only with objectMessageEnabled"),
           consumerReply = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.MAP },
                                     dependsOn = { "jmsMessageType", "messageConverter", "transferExchange" },
                                     byOption = @BodyType.When(option = "objectMessageEnabled", value = "true",
                                                               carriers = {
                                                                       Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                       Carrier.FILE, Carrier.MAP, Carrier.POJO }),
                                     description = "The reply, sent as the producer sends a message"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.MAP },
                                  dependsOn = { "jmsMessageType", "messageConverter", "transferExchange" },
                                  byOption = @BodyType.When(option = "objectMessageEnabled", value = "true",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                    Carrier.FILE, Carrier.MAP, Carrier.POJO }),
                                  description = "Text is sent as a TextMessage; bytes, a stream or a file as a BytesMessage;"
                                                + " a Map as a MapMessage. Another Serializable object fails unless"
                                                + " objectMessageEnabled"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "exchangePattern", value = "InOnly",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "disableReplyTo", value = "true",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "Unchanged for a message that expects no reply (InOnly). A message that"
                                                 + " expects one (InOut) gets the body of the reply, as the consumer gives it"))
public class AMQPEndpoint extends JmsEndpoint {

    @Override
    public String getServiceProtocol() {
        return "amqp";
    }
}
