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
package org.apache.camel.component.ahc;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.GlobalSSLContextParametersSupplier;

/**
 * Lookup from the registry should work when only one set of context parameters is present.
 */
public class AhcComponentClientConfigGlobalSslContextParametersTest extends AhcComponentClientConfigTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        SSLContextParameters params = registry.lookup("sslContextParameters", SSLContextParameters.class);
        registry.bind("sslContextParametersSupplier", (GlobalSSLContextParametersSupplier) () -> params);
        return registry;
    }

    @Override
    protected boolean isHttps() {
        return true;
    }

}
