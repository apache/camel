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
package org.apache.camel.component.kubernetes.properties;

import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecretMountPropertiesFunctionTest extends KubernetesTestSupport {

    @Test
    @Order(1)
    public void secretMountPropertiesFunction() throws Exception {
        try (SecretPropertiesFunction cmf = new SecretPropertiesFunction()) {
            cmf.setMountPathSecrets("src/test/resources/");
            cmf.setClientEnabled(false);
            cmf.setCamelContext(context);
            cmf.start();

            String out = cmf.apply("mysecret/myuser.txt");
            Assertions.assertEquals("donald", out);

            out = cmf.apply("mysecret/unknown");
            Assertions.assertNull(out);

            out = cmf.apply("mysecret/unknown:444");
            Assertions.assertEquals("444", out);

            out = cmf.apply("mysecret/mypass.txt");
            Assertions.assertEquals("seCre!t", out);
        }
    }

}
