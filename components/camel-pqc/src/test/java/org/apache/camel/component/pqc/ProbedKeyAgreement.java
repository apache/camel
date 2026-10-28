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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyAgreementSpi;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;

/**
 * A {@link KeyAgreement} that delegates to a real one and reports its calls to an {@link InterleavingProbe}.
 */
public final class ProbedKeyAgreement extends KeyAgreement {

    private ProbedKeyAgreement(KeyAgreement delegate, InterleavingProbe probe) {
        super(new Spi(delegate, probe), delegate.getProvider(), delegate.getAlgorithm());
    }

    public static KeyAgreement wrap(KeyAgreement delegate, InterleavingProbe probe) {
        return new ProbedKeyAgreement(delegate, probe);
    }

    private static final class Spi extends KeyAgreementSpi {

        private final KeyAgreement delegate;
        private final InterleavingProbe probe;

        Spi(KeyAgreement delegate, InterleavingProbe probe) {
            this.delegate = delegate;
            this.probe = probe;
        }

        @Override
        protected void engineInit(Key key, SecureRandom random) throws InvalidKeyException {
            delegate.init(key, random);
            probe.afterInit();
        }

        @Override
        protected void engineInit(Key key, AlgorithmParameterSpec params, SecureRandom random)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            delegate.init(key, params, random);
            probe.afterInit();
        }

        @Override
        protected Key engineDoPhase(Key key, boolean lastPhase) throws InvalidKeyException {
            return delegate.doPhase(key, lastPhase);
        }

        @Override
        protected byte[] engineGenerateSecret() {
            probe.beforeResult();
            return delegate.generateSecret();
        }

        @Override
        protected int engineGenerateSecret(byte[] sharedSecret, int offset) throws ShortBufferException {
            probe.beforeResult();
            return delegate.generateSecret(sharedSecret, offset);
        }

        @Override
        protected SecretKey engineGenerateSecret(String algorithm) throws NoSuchAlgorithmException, InvalidKeyException {
            probe.beforeResult();
            return delegate.generateSecret(algorithm);
        }
    }
}
