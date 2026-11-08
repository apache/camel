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
package org.apache.camel.component.grpc.auth.jwt;

import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtHelperTest {

    private static final String SECRET = "supersecuredsecret";

    /**
     * The jwtAlgorithm endpoint option advertises exactly the constants of {@link JwtAlgorithm}, so each of them has
     * to be backed by an implementation here. Without this, the option can offer a value that fails as soon as it is
     * used.
     */
    @Test
    void everyDeclaredAlgorithmIsImplemented() {
        for (JwtAlgorithm algorithm : JwtAlgorithm.values()) {
            Algorithm selected = JwtHelper.selectAlgorithm(algorithm, SECRET);
            assertNotNull(selected, () -> "no implementation for JwtAlgorithm." + algorithm);
        }
    }

    @Test
    void everyDeclaredAlgorithmSignsAToken() {
        for (JwtAlgorithm algorithm : JwtAlgorithm.values()) {
            String token = JwtHelper.createJwtToken(algorithm, SECRET, "issuer", "subject");
            assertNotNull(token, () -> "no token signed for JwtAlgorithm." + algorithm);
        }
    }
}
