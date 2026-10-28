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
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.KeyGenerator;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

/**
 * A {@link KeyGenerator} that delegates to a real one and reports its calls to an {@link InterleavingProbe}.
 */
public final class ProbedKeyGenerator extends KeyGenerator {

    private ProbedKeyGenerator(KeyGenerator delegate, InterleavingProbe probe) {
        super(new Spi(delegate, probe), delegate.getProvider(), delegate.getAlgorithm());
    }

    public static KeyGenerator wrap(KeyGenerator delegate, InterleavingProbe probe) {
        return new ProbedKeyGenerator(delegate, probe);
    }

    private static final class Spi extends KeyGeneratorSpi {

        private final KeyGenerator delegate;
        private final InterleavingProbe probe;

        Spi(KeyGenerator delegate, InterleavingProbe probe) {
            this.delegate = delegate;
            this.probe = probe;
        }

        @Override
        protected void engineInit(SecureRandom random) {
            delegate.init(random);
            probe.afterInit();
        }

        @Override
        protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
                throws InvalidAlgorithmParameterException {
            delegate.init(params, random);
            probe.afterInit();
        }

        @Override
        protected void engineInit(int keysize, SecureRandom random) {
            delegate.init(keysize, random);
            probe.afterInit();
        }

        @Override
        protected SecretKey engineGenerateKey() {
            probe.beforeResult();
            return delegate.generateKey();
        }
    }
}
