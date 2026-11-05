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
package org.apache.camel.component.hashicorp.vault;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.camel.util.ObjectHelper;

/**
 * The ways to log in to HashiCorp Vault, as chosen with the authentication option.
 */
enum HashicorpVaultAuthentication {

    TOKEN("token", "Token"),
    APPROLE("approle", "AppRole"),
    KUBERNETES("kubernetes", "Kubernetes"),
    JWT("jwt", "JWT");

    private final String value;
    private final String displayName;

    HashicorpVaultAuthentication(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    /**
     * The name of the login, as shown to people
     */
    String displayName() {
        return displayName;
    }

    /**
     * The login chosen with the given value of the authentication option: the token login when there is no value.
     *
     * @throws IllegalArgumentException when the value is not a login
     */
    static HashicorpVaultAuthentication of(String authentication) {
        if (ObjectHelper.isEmpty(authentication)) {
            return TOKEN;
        }
        String name = authentication.trim().toLowerCase(Locale.ROOT);
        for (HashicorpVaultAuthentication candidate : values()) {
            if (candidate.value.equals(name)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
                "Unknown HashiCorp Vault login: " + authentication + ". Use one of: "
                                           + Arrays.stream(values()).map(login -> login.value)
                                                   .collect(Collectors.joining(", ")));
    }
}
