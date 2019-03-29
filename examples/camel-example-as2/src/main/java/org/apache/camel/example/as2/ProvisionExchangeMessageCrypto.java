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
package org.apache.camel.example.as2;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.as2.AS2Component;
import org.apache.camel.component.as2.AS2Configuration;

public class ProvisionExchangeMessageCrypto implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {

        AS2Component component =  exchange.getContext().getComponent("as2", AS2Component.class);
        AS2Configuration configuration = component.getConfiguration();
        
        exchange.getIn().setHeader("CamelAS2.signingAlgorithm", configuration.getSigningAlgorithm());
        exchange.getIn().setHeader("CamelAS2.signingCertificateChain", configuration.getSigningCertificateChain());
        exchange.getIn().setHeader("CamelAS2.signingPrivateKey", configuration.getSigningPrivateKey());
        exchange.getIn().setHeader("CamelAS2.signedReceiptMicAlgorithms", configuration.getSignedReceiptMicAlgorithms());
        exchange.getIn().setHeader("CamelAS2.encryptingAlgorithm", configuration.getEncryptingAlgorithm());
        exchange.getIn().setHeader("CamelAS2.encryptingCertificateChain", configuration.getEncryptingCertificateChain());
        exchange.getIn().setHeader("CamelAS2.decryptingPrivateKey", configuration.getDecryptingPrivateKey());
        exchange.getIn().setHeader("CamelAS2.compressionAlgorithm", configuration.getCompressionAlgorithm());

    }

}
