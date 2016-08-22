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

import org.apache.camel.impl.DefaultHeaderFilterStrategy;

/**
 * @version 
 */
public class JmsHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public JmsHeaderFilterStrategy() {
        this(false);
    }

    public JmsHeaderFilterStrategy(boolean includeAllJMSXProperties) {
        if (!includeAllJMSXProperties) {
            initialize();
        }
    }

    protected void initialize() {
        // ignore provider specified JMS extension headers see page 39 of JMS 1.1 specification
        // added "JMSXRecvTimestamp" as a workaround for an Oracle bug/typo in AqjmsMessage
        getOutFilter().add("JMSXUserID");
        getOutFilter().add("JMSXAppID");
        getOutFilter().add("JMSXDeliveryCount");
        getOutFilter().add("JMSXProducerTXID");
        getOutFilter().add("JMSXConsumerTXID");
        getOutFilter().add("JMSXRcvTimestamp");
        getOutFilter().add("JMSXRecvTimestamp");
        getOutFilter().add("JMSXState");
    }

}
