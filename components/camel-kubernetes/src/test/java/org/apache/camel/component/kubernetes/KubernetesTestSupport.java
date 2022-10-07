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
package org.apache.camel.component.kubernetes;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesTestSupport extends CamelTestSupport {

    protected String authToken;
    protected String host;
    protected Logger log = LoggerFactory.getLogger(getClass());

    /*
     * NOTE: The Camel-Kubernetes tests were originally meant to be run along with the vagrant fabric8-image
     * https://github.com/fabric8io/fabric8-installer/tree/master/vagrant/openshift which would provide an
     * environment with Openshift/Kubernetes installed.
     *
     * However, since that image is deprecated, you can also run the tests with kind. See the README.md file
     * on the root of the component directory for details.
     */

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        // INSERT credentials and host here
        authToken = System.getProperty("kubernetes.test.auth");
        host = System.getProperty("kubernetes.test.host");
        super.setUp();
    }

    public static String toUrlEncoded(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }
}
