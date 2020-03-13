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
package org.apache.camel.component.rabbitmq.testbeans;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.TopologyRecoveryException;


public class CustomRabbitExceptionHandler implements ExceptionHandler {

    @Override
    public void handleUnexpectedConnectionDriverException(Connection connection, Throwable throwable) {

    }

    @Override
    public void handleReturnListenerException(Channel channel, Throwable throwable) {

    }

    @Override
    public void handleConfirmListenerException(Channel channel, Throwable throwable) {

    }

    @Override
    public void handleBlockedListenerException(Connection connection, Throwable throwable) {

    }

    @Override
    public void handleConsumerException(Channel channel, Throwable throwable, Consumer consumer, String s, String s1) {

    }

    @Override
    public void handleConnectionRecoveryException(Connection connection, Throwable throwable) {

    }

    @Override
    public void handleChannelRecoveryException(Channel channel, Throwable throwable) {

    }

    @Override
    public void handleTopologyRecoveryException(Connection connection, Channel channel, TopologyRecoveryException e) {

    }
}
