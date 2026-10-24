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
package org.apache.camel.component.pqc;

import java.security.Security;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

/**
 * Post Quantum Cryptography Signature and Verification component.
 */
@UriEndpoint(firstVersion = "4.12.0", scheme = "pqc", title = "PQC Algorithms",
             syntax = "pqc:label", producerOnly = true, category = { Category.SECURITY },
             headersClass = PQCConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "sign",
                                                         carriers = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM }),
                                          @BodyType.When(option = "operation", value = "verify",
                                                         carriers = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM }),
                                          @BodyType.When(option = "operation", value = "hybridSign",
                                                         carriers = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM }),
                                          @BodyType.When(option = "operation", value = "hybridVerify",
                                                         carriers = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM }),
                                          @BodyType.When(option = "operation", value = "extractSecretKeyEncapsulation",
                                                         carriers = Carrier.POJO,
                                                         javaType = "org.bouncycastle.jcajce.SecretKeyWithEncapsulation"),
                                          @BodyType.When(option = "operation", value = "extractSecretKeyFromEncapsulation",
                                                         carriers = Carrier.POJO,
                                                         javaType = "org.bouncycastle.jcajce.SecretKeyWithEncapsulation"),
                                          @BodyType.When(option = "operation", value = "importKey",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                 Carrier.FILE }) },
                                  description = "The payload of sign, verify, hybridSign and hybridVerify: bytes or a stream as"
                                                + " they are, text as UTF-8; another body is signed as its text, so marshal it"
                                                + " first. extractSecretKeyEncapsulation and extractSecretKeyFromEncapsulation"
                                                + " take the SecretKeyWithEncapsulation that generateSecretKeyEncapsulation"
                                                + " gives, and importKey the key as bytes, or as text for PEM. The other"
                                                + " operations do not use the body"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "generateSecretKeyEncapsulation",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.bouncycastle.jcajce.SecretKeyWithEncapsulation"),
                                           @BodyType.When(option = "operation", value = "extractSecretKeyEncapsulation",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.bouncycastle.jcajce.SecretKeyWithEncapsulation"),
                                           @BodyType.When(option = "operation", value = "extractSecretKeyFromEncapsulation",
                                                          carriers = Carrier.POJO, javaType = "javax.crypto.SecretKey"),
                                           @BodyType.When(option = "operation", value = "hybridGenerateSecretKeyEncapsulation",
                                                          carriers = Carrier.POJO, javaType = "javax.crypto.SecretKey"),
                                           @BodyType.When(option = "operation", value = "hybridExtractSecretKeyEncapsulation",
                                                          carriers = Carrier.POJO, javaType = "javax.crypto.SecretKey"),
                                           @BodyType.When(option = "operation",
                                                          value = "hybridExtractSecretKeyFromEncapsulation",
                                                          carriers = Carrier.POJO, javaType = "javax.crypto.SecretKey"),
                                           @BodyType.When(option = "storeExtractedSecretKeyAsHeader", value = "true",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "Unchanged for the signature and key operations, which set their results in"
                                                 + " headers, such as CamelPQCSignature and CamelPQCVerification."
                                                 + " generateSecretKeyEncapsulation and extractSecretKeyEncapsulation give a"
                                                 + " SecretKeyWithEncapsulation, and the other key encapsulation operations the"
                                                 + " SecretKey; with storeExtractedSecretKeyAsHeader=true the key is in the"
                                                 + " CamelPQCSecretKey header instead"))
public class PQCEndpoint extends DefaultEndpoint {

    @UriParam
    private PQCConfiguration configuration;

    public PQCEndpoint(String uri, Component component, PQCConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public PQCComponent getComponent() {
        return (PQCComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PQCProducer(this);
    }

    /**
     * Registers the BouncyCastle providers if they are not installed yet.
     * <p>
     * The providers are deliberately never removed again. {@link Security} is JVM-global and shared with every other
     * endpoint, every other {@code CamelContext} and all non-Camel code in the same JVM, and nothing records which
     * component installed a given provider. Removing one on stop therefore tears it out from under unrelated users -
     * including the sibling {@code pqc} endpoint of the same route and the statically initialised
     * {@code PQCDefault*Material} classes of this very component, which also register BouncyCastle and never remove it.
     */
    @Override
    public void doStart() throws Exception {
        super.doStart();
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    public PQCConfiguration getConfiguration() {
        return configuration;
    }
}
