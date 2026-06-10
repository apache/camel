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
package org.apache.camel.component.google.secret.manager.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class GoogleSecretManagerProtobufCompatibilityTest {

    @Test
    void protobufVersionCompatibleWithSecretManagerStubs() {
        // ListSecretsRequest.<clinit> references com.google.protobuf.RuntimeVersion$RuntimeDomain
        // which only exists in protobuf 4.x — if the BOM pins protobuf 3.x this will throw
        // NoClassDefFoundError at class-loading time
        assertDoesNotThrow(
                () -> Class.forName("com.google.cloud.secretmanager.v1.ListSecretsRequest"),
                "proto-google-cloud-secretmanager-v1 stubs are incompatible with the protobuf version on the classpath");
    }
}
