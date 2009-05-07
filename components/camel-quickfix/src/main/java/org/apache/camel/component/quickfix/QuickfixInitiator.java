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
package org.apache.camel.component.quickfix;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultComponent;
import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.LogFactory;
import quickfix.MessageStoreFactory;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;

/**
 * QuickfixInitiator is the endpoint for QuickFIX/J's initiator instance, e.g.
 * FIX client. The class is referenced in
 * META-INF/services/org/apache/camel/quickfix-client
 * 
 * @author Anton Arhipov
 */
public class QuickfixInitiator extends DefaultComponent {
    protected QuickfixEndpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        return new QuickfixInitiatorEndpoint(uri, getCamelContext(), remaining);
    }

    class QuickfixInitiatorEndpoint extends QuickfixEndpoint {

        private/* Threaded */SocketInitiator initiator;

        public QuickfixInitiatorEndpoint(String uri, CamelContext context, String configuration) {
            super(uri, context, configuration);
        }

        protected void start(Application application, MessageStoreFactory storeFactory,
                             SessionSettings settings, LogFactory logFactory) throws ConfigError {

            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                initiator = new SocketInitiator(application, storeFactory, settings, logFactory,
                                                new DefaultMessageFactory());

                initiator.start();
            } finally {
                Thread.currentThread().setContextClassLoader(ccl);
            }
        }

        public void stop() throws Exception {
            super.stop();
            if (initiator != null) {
                initiator.stop();
                initiator = null;
            }
        }
    }
}
