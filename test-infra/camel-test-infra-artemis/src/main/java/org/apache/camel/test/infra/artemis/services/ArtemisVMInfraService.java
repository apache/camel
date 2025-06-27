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
package org.apache.camel.test.infra.artemis.services;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.camel.test.infra.artemis.common.ArtemisRunException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtemisVMInfraService extends AbstractArtemisEmbeddedService {
    private static final Logger LOG = LoggerFactory.getLogger(ArtemisVMInfraService.class);

    private String brokerURL;

    public ArtemisVMInfraService() {
    }

    protected ArtemisVMInfraService(int port) {
        super(port);
    }

    /**
     * This class should rarely be used. It is intended for some tests that check for reliability operations and require
     * using the same broker ID between start/stop cycles.
     */
    public static class ReusableArtemisVMService extends ArtemisVMInfraService {

        public ReusableArtemisVMService(int port) {
            super(port);
        }

        @Override
        protected int computeBrokerId() {
            return 0;
        }
    }

    @Override
    protected Configuration configure(Configuration configuration, int port, int brokerId) {
        brokerURL = "vm://" + brokerId;

        LOG.info("Creating a new Artemis VM-based broker");
        configuration.setPersistenceEnabled(false);
        configuration.setJournalMinFiles(10);
        configuration.setSecurityEnabled(false);

        try {
            configuration.addAcceptorConfiguration("in-vm", "vm://" + brokerId);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            throw new ArtemisRunException("vm acceptor cannot be configured", e);
        }
        configuration.addAddressSetting("#",
                new AddressSettings()
                        .setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL)
                        .setAutoDeleteQueues(false)
                        .setDeadLetterAddress(SimpleString.of("DLQ"))
                        .setExpiryAddress(SimpleString.of("ExpiryQueue")));

        return configuration;
    }

    @Override
    public String serviceAddress() {
        return brokerURL;
    }

    @Override
    public int brokerPort() {
        return 0;
    }
}
