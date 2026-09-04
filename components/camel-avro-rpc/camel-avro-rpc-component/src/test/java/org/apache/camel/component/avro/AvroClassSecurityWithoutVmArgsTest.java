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
package org.apache.camel.component.avro;

import org.apache.avro.ipc.HandshakeRequest;
import org.apache.avro.util.ClassSecurityValidator;
import org.apache.camel.avro.generated.Key;
import org.apache.camel.avro.support.AvroClassSecuritySupport;
import org.apache.camel.avro.test.TestPojo;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AvroClassSecurityWithoutVmArgsTest extends CamelTestSupport {

    @BeforeEach
    void resetValidator() {
        AvroClassSecuritySupport.resetForTesting();
    }

    @Test
    void shouldTrustAvroIpcHandshakeClassesFromComponentInit() throws Exception {
        context.getEndpoint(
                "avro:netty:localhost:9999?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol",
                AvroEndpoint.class);

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(HandshakeRequest.class));
    }

    @Test
    void shouldTrustProtocolModelPackagesFromEndpointConfiguration() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:validate")
                        .to("avro:netty:localhost:9999?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol");
            }
        });
        context.getEndpoint(
                "avro:netty:localhost:9999?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol",
                AvroEndpoint.class);

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Key.class));
    }

    @Test
    void shouldTrustAdditionalPackagesFromSerializablePackagesOption() throws Exception {
        context.getEndpoint(
                "avro:netty:localhost:9999?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol"
                            + "&serializablePackages=org.apache.camel.avro.generated",
                AvroEndpoint.class);

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Key.class));
    }

    @Test
    void shouldTrustParentPackageFromSerializablePackagesOption() throws Exception {
        context.getEndpoint(
                "avro:netty:localhost:9999?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol"
                            + "&serializablePackages=org.apache.camel.avro",
                AvroEndpoint.class);

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(TestPojo.class));
    }

    @Test
    void shouldRejectClassesOutsideConfiguredProtocolPackages() throws Exception {
        context.getEndpoint(
                "avro:netty:localhost:9999?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol",
                AvroEndpoint.class);

        assertThrows(SecurityException.class, () -> ClassSecurityValidator.validate(TestPojo.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // route added in test
            }
        };
    }
}
