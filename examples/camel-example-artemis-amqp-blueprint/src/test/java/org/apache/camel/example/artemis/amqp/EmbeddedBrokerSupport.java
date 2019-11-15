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
package org.apache.camel.example.artemis.amqp;

import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;

// import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
// import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;


//#################################################
// Blueprint does not support Bean inheritance (necessary for Artemis EmbeddedJMS)
// We need therefore a 'support' class
//#################################################
public class EmbeddedBrokerSupport extends EmbeddedJMS {
    
    public EmbeddedBrokerSupport(ActiveMQJAASSecurityManager securityManager) throws Exception {
        this.setSecurityManager(securityManager);
        this.start();

        //if you need more twicking use Java to customise as follows:
            // SecurityConfiguration securityConfig = new SecurityConfiguration();
            // securityConfig.addUser("guest", "guest");
            // securityConfig.addRole("guest", "guest");
            // securityConfig.setDefaultUser("guest");
            // ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfig);
            // this.setSecurityManager(securityManager);
    }

    public void close() throws Exception {
        this.stop();
    }
}
