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

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A shutdown hook that can be used to shutdown {@link CamelContext} before the
 * ActiveMQ broker is shut down. This is sometimes important as if the broker is
 * shutdown before Camel there could be a loss of data due to inflight exchanges
 * not yet completed.
 * <p>
 * This hook can be added to ActiveMQ configuration ({@code activemq.xml}) as in
 * the following example:
 * <p>
 * <code>
 * &lt;bean xmlns=&quot;http://www.springframework.org/schema/beans&quot; class=&quot;org.apache.activemq.camel.CamelShutdownHook&quot; /&gt;
 * </code>
 */
public final class CamelShutdownHook implements Runnable, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(CamelShutdownHook.class);

    private CamelContext camelContext;

    @Autowired
    public CamelShutdownHook(final BrokerService brokerService) {
        brokerService.addPreShutdownHook(this);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void run() {
        if (camelContext != null) {
            try {
                camelContext.stop();
            } catch (final Exception e) {
                LOG.warn("Unable to stop CamelContext", e);
            }
        } else {
            LOG.warn("Unable to stop CamelContext, no CamelContext was set!");
        }
    }

    @Override
    public void setCamelContext(final CamelContext camelContext) {
        this.camelContext = camelContext;
    }

}
