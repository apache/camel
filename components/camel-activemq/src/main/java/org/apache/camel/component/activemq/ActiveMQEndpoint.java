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
package org.apache.camel.component.activemq;

import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.spi.UriEndpoint;

/**
 * The activemq component allows messages to be sent to (or consumed from)
 * Apache ActiveMQ. This component extends the Camel JMS component.
 */
@UriEndpoint(firstVersion = "1.0.0", extendsScheme = "jms", scheme = "activemq", title = "ActiveMQ", syntax = "activemq:destinationType:destinationName",
        label = "messaging")
public class ActiveMQEndpoint extends JmsEndpoint {

    // needed for component documentation

}
