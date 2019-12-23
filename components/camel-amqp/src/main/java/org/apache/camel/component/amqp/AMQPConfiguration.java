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

import javax.jms.ConnectionFactory;

import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class AMQPConfiguration extends JmsConfiguration {

    @UriParam(label = "consumer,advanced",
        description = "Whether to include AMQP annotations when mapping from AMQP to Camel Message."
            + " Setting this to true will map AMQP message annotations to message headers."
            + " Due to limitations in Apache Qpid JMS API, currently delivery annotations are ignored.")
    private boolean includeAmqpAnnotations;

    public AMQPConfiguration() {
        super();
    }

    public AMQPConfiguration(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public boolean isIncludeAmqpAnnotations() {
        return includeAmqpAnnotations;
    }

    /**
     * Whether to include AMQP annotations when mapping from AMQP to Camel Message.
     * Setting this to true will map AMQP message annotations to message headers.
     * Due to limitations in Apache Qpid JMS API, currently delivery annotations
     * are ignored.
     */
    public void setIncludeAmqpAnnotations(boolean includeAmqpAnnotations) {
        this.includeAmqpAnnotations = includeAmqpAnnotations;
    }

}
