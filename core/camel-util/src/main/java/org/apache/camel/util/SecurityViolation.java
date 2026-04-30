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
package org.apache.camel.util;

/**
 * Represents a security policy violation detected during configuration analysis.
 *
 * @param category    the security category (e.g., "secret", "insecure:ssl", "insecure:serialization", "insecure:dev")
 * @param propertyKey the full property key (e.g., "camel.ssl.trustAllCertificates")
 * @param message     human-readable description of the violation
 * @param policy      the effective policy that was resolved for this violation ("warn" or "fail")
 *
 * @since             4.19.0
 */
public record SecurityViolation(String category, String propertyKey, String message, String policy) {

    @Override
    public String toString() {
        return "[" + category + "] " + message + " [" + propertyKey + "]";
    }
}
