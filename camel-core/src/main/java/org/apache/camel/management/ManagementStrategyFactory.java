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
package org.apache.camel.management;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating {@link ManagementStrategy}
 */
public class ManagementStrategyFactory {
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    public ManagementStrategy create(CamelContext context, boolean disableJMX) {
        ManagementStrategy answer = null;

        if (disableJMX || Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
            log.info("JMX is disabled.");
        } else {
            try {
                answer = new ManagedManagementStrategy(new DefaultManagementAgent(context));
                // must start it to ensure JMX works and can load needed Spring JARs
                ServiceHelper.startService(answer);
                // prefer to have it at first strategy
                context.getLifecycleStrategies().add(0, new DefaultManagementLifecycleStrategy(context));
                log.info("JMX enabled.");
            } catch (Exception e) {
                answer = null;
                log.warn("Cannot create JMX lifecycle strategy. Will fallback and disable JMX.", e);
            }
        }

        if (answer == null) {
            answer = new DefaultManagementStrategy();
        }
        return answer;
    }
}
