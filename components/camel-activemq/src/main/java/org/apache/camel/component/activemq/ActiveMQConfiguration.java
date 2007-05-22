/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.activemq;

import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.activemq.spring.ActiveMQConnectionFactory;
import org.apache.camel.component.jms.JmsConfiguration;

import javax.jms.ConnectionFactory;

/**
 * @version $Revision: 1.1 $
 */
public class ActiveMQConfiguration extends JmsConfiguration {
    private String brokerURL = ActiveMQConnectionFactory.DEFAULT_BROKER_URL;

    public ActiveMQConfiguration() {
    }

    public String getBrokerURL() {
        return brokerURL;
    }

    /**
     * Sets the broker URL to use to connect to ActiveMQ using the
     * <a href="http://activemq.apache.org/configuring-transports.html">ActiveMQ URI format</a>
     *
     * @param brokerURL the URL of the broker.
     */
    public void setBrokerURL(String brokerURL) {
        this.brokerURL = brokerURL;
    }

    @Override
    public ActiveMQConnectionFactory getListenerConnectionFactory() {
        return (ActiveMQConnectionFactory) super.getListenerConnectionFactory();
    }

    @Override
    public void setListenerConnectionFactory(ConnectionFactory listenerConnectionFactory) {
        if (listenerConnectionFactory instanceof ActiveMQConnectionFactory) {
            super.setListenerConnectionFactory(listenerConnectionFactory);
        }
        else {
            throw new IllegalArgumentException("ConnectionFactory " + listenerConnectionFactory
                    + " is not an instanceof " + ActiveMQConnectionFactory.class.getName());
        }
    }

    @Override
    protected ConnectionFactory createListenerConnectionFactory() {
        ActiveMQConnectionFactory answer = new ActiveMQConnectionFactory();
        answer.setBrokerURL(getBrokerURL());
        return answer;
    }

    @Override
    protected ConnectionFactory createTemplateConnectionFactory() {
        return new PooledConnectionFactory(getListenerConnectionFactory());
    }
}
