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
package org.apache.camel.component.grpc;

import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcConsumerConfigurationTest extends CamelTestSupport {
    @Test
    void emptyHostPort() {
        FailedToCreateConsumerException exception = assertThrows(FailedToCreateConsumerException.class,
                () -> consumer.receive("grpc:/org.apache.camel.component.grpc.PingPong"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void emptyPort() {
        FailedToCreateConsumerException exception = assertThrows(FailedToCreateConsumerException.class,
                () -> consumer.receive("grpc:localhost/org.apache.camel.component.grpc.PingPong"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void invalidPort() {
        FailedToCreateConsumerException exception = assertThrows(FailedToCreateConsumerException.class,
                () -> consumer.receive("grpc:localhost:0/org.apache.camel.component.grpc.PingPong"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void invalidMaxRstFramesPerWindowWithValidMaxRstPeriodSeconds() {
        FailedToCreateConsumerException exception = assertThrows(FailedToCreateConsumerException.class,
                () -> consumer.receive(
                        "grpc:localhost:8080/org.apache.camel.component.grpc.PingPong?maxRstFramesPerWindow=-1&maxRstPeriodSeconds=5"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void missingMaxRstFramesPerWindowWithValidMaxRstPeriodSeconds() {
        FailedToCreateConsumerException exception = assertThrows(FailedToCreateConsumerException.class,
                () -> consumer.receive(
                        "grpc:localhost:8080/org.apache.camel.component.grpc.PingPong?maxRstPeriodSeconds=5"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void invalidMaxRstPeriodSecondsWithValidMaxRstFramesPerWindow() {
        FailedToCreateConsumerException exception = assertThrows(FailedToCreateConsumerException.class,
                () -> consumer.receive(
                        "grpc:localhost:8080/org.apache.camel.component.grpc.PingPong?maxRstFramesPerWindow=100&maxRstPeriodSeconds=-1"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void missingMaxRstPeriodSecondsWithValidMaxRstFramesPerWindow() {
        FailedToCreateConsumerException exception = assertThrows(FailedToCreateConsumerException.class,
                () -> consumer.receive(
                        "grpc:localhost:8080/org.apache.camel.component.grpc.PingPong?maxRstFramesPerWindow=100"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }
}
