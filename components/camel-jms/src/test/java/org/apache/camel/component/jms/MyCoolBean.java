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

package org.apache.camel.component.jms;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.IllegalStateException;

final class MyCoolBean {
    private static final Logger LOG = LoggerFactory.getLogger(MyCoolBean.class);

    private int count;
    private final ConsumerTemplate consumer;
    private final ProducerTemplate producer;
    private final String queueName;

    public MyCoolBean(ConsumerTemplate consumer, ProducerTemplate producer, String queueName) {
        this.consumer = consumer;
        this.producer = producer;
        this.queueName = queueName;
    }

    public void someBusinessLogic() {
        // loop to empty queue
        while (true) {
            // receive the message from the queue, wait at most 2 sec
            try {
                String msg = consumer.receiveBody("activemq:" + queueName + ".in", 2000, String.class);
                if (msg == null) {
                    // no more messages in queue
                    break;
                }
                // do something with body
                msg = "Hello " + msg;

                // send it to the next queue
                producer.sendBodyAndHeader("activemq:" + queueName + ".out", msg, "number", count++);
            } catch (IllegalStateException e) {
                if (e.getCause() instanceof jakarta.jms.IllegalStateException) {
                    // session is closed
                    LOG.warn("JMS Session is closed");
                    break;
                }
            }

        }
    }
}
