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
package org.apache.camel.dsl.yaml

import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.support.jsse.SSLContextParameters

class SSLContextParametersTest extends YamlTestSupport {

    def "load ssl context parameters"() {
        when:
            loadRoutesNoValidate """
                - sslContextParameters:
                    id: mySSL
                    keyStore: server.p12
                    keystorePassword: changeit
                    trustStore: truststore.p12
                    trustStorePassword: changeit
                - from:
                    uri: "direct:ssl"
                    steps:
                      - to: "mock:ssl"
            """
        then:
            // verify SSL context parameters were registered in registry
            def sslParams = context.registry.lookupByNameAndType('mySSL', SSLContextParameters)
            sslParams != null

            // verify it was also set as the global default
            context.getSSLContextParameters() != null

            // verify key store configuration
            sslParams.keyManagers != null
            sslParams.keyManagers.keyStore != null
            sslParams.keyManagers.keyStore.resource == 'server.p12'
            sslParams.keyManagers.keyPassword == 'changeit'

            // verify trust store configuration
            sslParams.trustManagers != null
            sslParams.trustManagers.keyStore != null
            sslParams.trustManagers.keyStore.resource == 'truststore.p12'
            sslParams.trustManagers.keyStore.password == 'changeit'
    }

    def "load ssl context parameters with advanced options"() {
        when:
            loadRoutesNoValidate """
                - sslContextParameters:
                    id: myAdvancedSSL
                    keyStore: server.p12
                    keystorePassword: changeit
                    secureSocketProtocol: TLSv1.3
                    certAlias: myAlias
                    clientAuthentication: WANT
                - from:
                    uri: "direct:ssl"
                    steps:
                      - to: "mock:ssl"
            """
        then:
            def sslParams = context.registry.lookupByNameAndType('myAdvancedSSL', SSLContextParameters)
            sslParams != null
            sslParams.secureSocketProtocol == 'TLSv1.3'
            sslParams.certAlias == 'myAlias'
            sslParams.serverParameters != null
            sslParams.serverParameters.clientAuthentication == 'WANT'
    }

    def "load ssl context parameters with trust all certificates"() {
        when:
            loadRoutesNoValidate """
                - sslContextParameters:
                    id: myTrustAllSSL
                    trustAllCertificates: "true"
                - from:
                    uri: "direct:ssl"
                    steps:
                      - to: "mock:ssl"
            """
        then:
            def sslParams = context.registry.lookupByNameAndType('myTrustAllSSL', SSLContextParameters)
            sslParams != null
            // trust managers should be configured with trust-all
            sslParams.trustManagers != null
            // key managers should be null since no keyStore was provided
            sslParams.keyManagers == null
    }

    def "load ssl context parameters without id sets global default"() {
        when:
            loadRoutesNoValidate """
                - sslContextParameters:
                    keyStore: server.p12
                    keystorePassword: changeit
                - from:
                    uri: "direct:ssl"
                    steps:
                      - to: "mock:ssl"
            """
        then:
            // verify global default is set even without id
            context.getSSLContextParameters() != null
            context.getSSLContextParameters().keyManagers != null
            context.getSSLContextParameters().keyManagers.keyStore.resource == 'server.p12'
    }
}
