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

import java.security.Key;

import javax.crypto.KeyGenerator;

/**
 * Chooses the monitor that serializes one KEM {@code init()} / {@code generateKey()} sequence.
 * <p>
 * A KEM {@link KeyGenerator} is not thread-safe, so the sequence normally holds the monitor of the generator it uses.
 * That is not enough for SABER: every SABER key of a parameter set points to one static Bouncy Castle
 * {@code SABERParameters} constant, which owns a single {@code SABEREngine} whose SHA-3 state is mutated by every
 * encapsulation and decapsulation. Two threads using different {@code KeyGenerator} instances - for example the
 * generators a producer creates for each exchange - still corrupt each other ("attempt to absorb while squeezing", or a
 * secret that differs from the one extracted). All SABER operations of this JVM therefore share one monitor.
 */
public final class PQCKemLocks {

    private static final Object SABER_LOCK = new Object();

    private PQCKemLocks() {
    }

    /**
     * @param  generator the KEM key generator the sequence is going to use
     * @param  key       the public key (encapsulate) or private key (extract) given to the generator
     * @return           the object to synchronize the whole {@code init()} / {@code generateKey()} sequence on
     */
    public static Object lockFor(KeyGenerator generator, Key key) {
        if (key != null && PQCKeyEncapsulationAlgorithms.SABER.getAlgorithm().equals(key.getAlgorithm())) {
            return SABER_LOCK;
        }
        return generator;
    }

    /**
     * Whether the Bouncy Castle implementation of the algorithm keeps mutable state outside the {@code KeyGenerator},
     * so that {@link #lockFor(KeyGenerator, Key)} has to hand out one monitor for all generators of the algorithm.
     * <p>
     * Only SABER does. The other KEMs were checked with concurrent encapsulation and extraction on separate generators
     * and keep their state in the generator or the key. HQC and SmaugT also hold an engine in their static parameter
     * constants, but it only contains immutable sizes and tables.
     */
    public static boolean sharesEngineAcrossKeyGenerators(PQCKeyEncapsulationAlgorithms algorithm) {
        return algorithm == PQCKeyEncapsulationAlgorithms.SABER;
    }
}
