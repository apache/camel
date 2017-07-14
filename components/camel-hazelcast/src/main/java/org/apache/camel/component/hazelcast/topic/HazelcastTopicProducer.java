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
package org.apache.camel.component.hazelcast.topic;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;
import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.component.hazelcast.HazelcastOperation;

/**
 *
 */
public class HazelcastTopicProducer extends HazelcastDefaultProducer {

    private ITopic<Object> topic;

    public HazelcastTopicProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint, String topicName, boolean reliable) {
        super(endpoint);
        if (!reliable) {
            this.topic = hazelcastInstance.getTopic(topicName);
        } else {
            this.topic = hazelcastInstance.getReliableTopic(topicName);
        }
    }

    public void process(Exchange exchange) throws Exception {
        final HazelcastOperation operation = lookupOperation(exchange);

        switch (operation) {

        case PUBLISH:
            this.publish(exchange);
            break;
        default:
            throw new IllegalArgumentException(String.format("The value '%s' is not allowed for parameter '%s' on the TOPIC cache.", operation, HazelcastConstants.OPERATION));
        }

         // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);
    }

    private void publish(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.topic.publish(body);
    }
}
