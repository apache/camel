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

package org.apache.camel.component.netty.http;

import org.apache.camel.component.netty.NettyServerBootstrapConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InitializerHelper {
    private static final Logger LOG = LoggerFactory.getLogger(InitializerHelper.class);

    private InitializerHelper() {

    }

    static void logConfiguration(NettyServerBootstrapConfiguration configuration) {
        if (configuration.getKeyStoreFile() == null && configuration.getKeyStoreResource() == null) {
            LOG.debug("keystorefile is null");
        }
        if (configuration.getTrustStoreFile() == null && configuration.getTrustStoreResource() == null) {
            LOG.debug("truststorefile is null");
        }
        if (configuration.getPassphrase() == null) {
            LOG.debug("passphrase is null");
        }
    }
}
