/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.apache.camel.component.cxf.util;

import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.GlobalSSLContextParametersSupplier;

/**
 * Class for binding a SSSLContextParametersSupplier to the registry.
 */
public class CxfSSLContextParameterSupplier implements GlobalSSLContextParametersSupplier {

    private SSLContextParameters sslContextParameters;

    public CxfSSLContextParameterSupplier() {
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    @Override
    public SSLContextParameters get() {
        return sslContextParameters;
    }
}
