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
package org.apache.camel.component.activemq;

import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.spring.ActiveMQConnectionFactory;
import org.apache.activemq.transport.Transport;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;

/**
 * A JMS ConnectionFactory which resolves non-JMS destinations or instances of
 * {@link CamelDestination} to use the {@link CamelContext} to perform smart
 * routing etc
 */
public class CamelConnectionFactory extends ActiveMQConnectionFactory implements CamelContextAware {
    private CamelContext camelContext;

    public CamelConnectionFactory() {
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    protected CamelConnection createActiveMQConnection(Transport transport, JMSStatsImpl stats) throws Exception {
        CamelConnection connection = new CamelConnection(transport, getClientIdGenerator(), getConnectionIdGenerator(), stats);
        CamelContext context = getCamelContext();
        if (context != null) {
            connection.setCamelContext(context);
        }
        return connection;
    }
}
