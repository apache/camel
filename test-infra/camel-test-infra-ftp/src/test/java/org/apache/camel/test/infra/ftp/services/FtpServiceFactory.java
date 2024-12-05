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
package org.apache.camel.test.infra.ftp.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.ftp.services.embedded.EmbeddedConfiguration;
import org.apache.camel.test.infra.ftp.services.embedded.FtpEmbeddedService;
import org.apache.camel.test.infra.ftp.services.embedded.FtpsEmbeddedService;

public final class FtpServiceFactory {

    private FtpServiceFactory() {

    }

    public static SimpleTestServiceBuilder<FtpEmbeddedService> embeddedBuilder() {
        return new SimpleTestServiceBuilder<>("ftp");
    }

    public static FtpEmbeddedService createEmbeddedService() {
        return embeddedBuilder()
                .addLocalMapping(FtpEmbeddedService::new)
                .build();
    }

    public static FtpsEmbeddedService createSecureEmbeddedService(
            EmbeddedConfiguration.SecurityConfiguration securityConfiguration) {
        return new FtpsEmbeddedService(securityConfiguration);
    }
}
