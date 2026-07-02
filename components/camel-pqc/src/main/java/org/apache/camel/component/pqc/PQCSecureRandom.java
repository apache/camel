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

import java.security.SecureRandom;

/**
 * Shared {@link SecureRandom} instance for the PQC component.
 * <p>
 * {@code SecureRandom} is thread-safe but heavyweight to instantiate (each call gathers OS entropy). Reusing a single
 * instance avoids repeated initialization overhead while maintaining cryptographic security guarantees.
 */
public final class PQCSecureRandom {

    /**
     * Shared secure random instance. Thread-safe by contract of {@link SecureRandom}.
     */
    public static final SecureRandom RANDOM = new SecureRandom();

    private PQCSecureRandom() {
    }
}
