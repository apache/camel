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
package org.apache.camel.component.jms;

import java.util.Random;
import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * A helper for unit testing with Apache ActiveMQ as embedded JMS broker.
 *
 * @version $Revision$
 */
public final class CamelJmsTestHelper {

    private static Random ran = new Random();

    private CamelJmsTestHelper() {
    }

    public static ConnectionFactory createConnectionFactory() {
        // using a unique broker name improves testing when running the entire test suite in the same JVM
        int id = ran.nextInt(100000);
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker-" + id + "?broker.persistent=false&broker.useJmx=false");
        return connectionFactory;
    }

    public static ConnectionFactory createPersistentConnectionFactory() {
        // using a unique broker name improves testing when running the entire test suite in the same JVM
        int id = ran.nextInt(100000);
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker-" + id + "?broker.persistent=true&broker.useJmx=false");
        return connectionFactory;
    }
}
