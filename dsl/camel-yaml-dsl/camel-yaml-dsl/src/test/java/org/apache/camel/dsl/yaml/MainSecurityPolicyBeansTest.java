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
package org.apache.camel.dsl.yaml;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.yaml.support.model.MyTlsClient;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The security policy checks the properties of the beans in the beans section of a YAML route file, as it checks a bean
 * configured from camel.beans.* properties.
 */
class MainSecurityPolicyBeansTest {

    @Test
    void insecureBeanPropertyFailsStartupWithProdProfile() {
        Main main = new Main();
        try {
            main.addInitialProperty("camel.main.profile", "prod");
            main.configure().withRoutesIncludePattern("security/route-beans.yaml");

            assertThatThrownBy(main::start)
                    .isInstanceOf(RuntimeCamelException.class)
                    .hasMessageContaining("[insecure:ssl]")
                    .hasMessageContaining("[camel.beans.myClient.trustAllCertificates]");
        } finally {
            main.stop();
        }
    }

    @Test
    void allowedBeanPropertyStartsWithProdProfile() {
        Main main = new Main();
        try {
            main.addInitialProperty("camel.main.profile", "prod");
            main.addInitialProperty("camel.security.allowedProperties", "camel.beans.myClient.trustAllCertificates");
            main.configure().withRoutesIncludePattern("security/route-beans.yaml");
            main.start();

            MyTlsClient client = main.getCamelContext().getRegistry().lookupByNameAndType("myClient", MyTlsClient.class);
            assertThat(client).isNotNull();
            assertThat(client.isTrustAllCertificates()).isTrue();
        } finally {
            main.stop();
        }
    }
}
