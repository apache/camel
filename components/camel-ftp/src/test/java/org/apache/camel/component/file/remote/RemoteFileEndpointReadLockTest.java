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
package org.apache.camel.component.file.remote;

import java.util.stream.Stream;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumer;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.remote.strategy.FtpChangedExclusiveReadLockStrategy;
import org.apache.camel.component.file.remote.strategy.SftpChangedExclusiveReadLockStrategy;
import org.apache.camel.component.file.strategy.GenericFileNoOpProcessStrategy;
import org.apache.camel.component.file.strategy.GenericFileProcessStrategySupport;
import org.apache.camel.component.file.strategy.GenericFileRenameExclusiveReadLockStrategy;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The ftp, ftps and sftp consumers implement the read locks none, rename and changed, and false and off mean none. Any
 * other readLock, such as one that only the file component implements, fails when the consumer is created, instead of
 * consuming the files without a read lock. The readLock is not checked when a custom read lock or process strategy is
 * set, as it does not apply then.
 */
class RemoteFileEndpointReadLockTest extends CamelTestSupport {

    private static final String[] SCHEMES = { "ftp", "ftps", "sftp" };

    @BindToRegistry("repo")
    private final IdempotentRepository repo = MemoryIdempotentRepository.memoryIdempotentRepository();

    @BindToRegistry("myLock")
    private final GenericFileExclusiveReadLockStrategy<?> myLock = new GenericFileRenameExclusiveReadLockStrategy<>();

    @BindToRegistry("myStrategy")
    private final GenericFileProcessStrategy<?> myStrategy = new GenericFileNoOpProcessStrategy<>();

    static Stream<Arguments> unsupportedReadLocks() {
        return Stream.of(SCHEMES).flatMap(scheme -> Stream
                .of("markerFile", "fileLock", "idempotent", "idempotent-changed", "idempotent-rename", "chnaged")
                .map(readLock -> Arguments.of(scheme, readLock)));
    }

    static Stream<Arguments> customStrategies() {
        return Stream.of(SCHEMES)
                .flatMap(scheme -> Stream.of("exclusiveReadLockStrategy=#myLock", "processStrategy=#myStrategy")
                        .map(option -> Arguments.of(scheme, option)));
    }

    static Stream<Arguments> supportedReadLocks() {
        return Stream.of(SCHEMES).flatMap(scheme -> Stream.of("none", "false", "off", "rename", "changed")
                .map(readLock -> Arguments.of(scheme, readLock)));
    }

    @ParameterizedTest
    @MethodSource("unsupportedReadLocks")
    void unsupportedReadLockFailsToCreateConsumer(String scheme, String readLock) {
        Endpoint endpoint
                = context.getEndpoint(scheme + "://localhost/inbox?readLock=" + readLock + "&idempotentRepository=#repo");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> endpoint.createConsumer(exchange -> {
        }));
        assertEquals("ReadLock invalid: " + readLock + ", must be one of: none,rename,changed", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "sftp" })
    void unsupportedReadLockFailsToCreatePollingConsumer(String scheme) throws Exception {
        // poll and pollEnrich create the consumer through the polling consumer
        Endpoint endpoint
                = context.getEndpoint(scheme + "://localhost/inbox?readLock=idempotent-changed&idempotentRepository=#repo");
        PollingConsumer consumer = endpoint.createPollingConsumer();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ServiceHelper.buildService(consumer));
        assertEquals("ReadLock invalid: idempotent-changed, must be one of: none,rename,changed", e.getMessage());
    }

    @ParameterizedTest
    @MethodSource("supportedReadLocks")
    void supportedReadLockCreatesConsumer(String scheme, String readLock) throws Exception {
        Endpoint endpoint = context.getEndpoint(scheme + "://localhost/inbox?readLock=" + readLock);

        assertNotNull(endpoint.createConsumer(exchange -> {
        }));
    }

    @Test
    void supportedReadLockGivesTheReadLock() {
        assertNull(ftpReadLock("none"));
        assertNull(ftpReadLock("false"));
        assertNull(ftpReadLock("off"));
        assertInstanceOf(GenericFileRenameExclusiveReadLockStrategy.class, ftpReadLock("rename"));
        assertInstanceOf(FtpChangedExclusiveReadLockStrategy.class, ftpReadLock("changed"));

        assertNull(sftpReadLock("none"));
        assertNull(sftpReadLock("false"));
        assertNull(sftpReadLock("off"));
        assertInstanceOf(GenericFileRenameExclusiveReadLockStrategy.class, sftpReadLock("rename"));
        assertInstanceOf(SftpChangedExclusiveReadLockStrategy.class, sftpReadLock("changed"));
    }

    @ParameterizedTest
    @MethodSource("customStrategies")
    void readLockIsNotCheckedWithCustomStrategy(String scheme, String option) throws Exception {
        // the readLock option does not apply when a custom read lock or process strategy is set
        Endpoint endpoint = context.getEndpoint(scheme + "://localhost/inbox?readLock=markerFile&" + option);

        assertNotNull(endpoint.createConsumer(exchange -> {
        }));
    }

    @Test
    void customReadLockIsUsed() {
        FtpEndpoint<?> endpoint = context.getEndpoint(
                "ftp://localhost/inbox?readLock=markerFile&exclusiveReadLockStrategy=#myLock", FtpEndpoint.class);

        assertSame(myLock,
                ((GenericFileProcessStrategySupport<?>) endpoint.createGenericFileStrategy()).getExclusiveReadLockStrategy());
    }

    @Test
    void unsupportedReadLockDoesNotFailTheProducer() throws Exception {
        // the read lock is a consumer option, so an endpoint uri shared by from and to still creates a producer
        Endpoint endpoint = context.getEndpoint("ftp://localhost/outbox?readLock=markerFile");

        assertNotNull(endpoint.createProducer());
    }

    private Object ftpReadLock(String readLock) {
        FtpEndpoint<?> endpoint = context.getEndpoint("ftp://localhost/inbox?readLock=" + readLock, FtpEndpoint.class);
        return ((GenericFileProcessStrategySupport<?>) endpoint.createGenericFileStrategy()).getExclusiveReadLockStrategy();
    }

    private Object sftpReadLock(String readLock) {
        SftpEndpoint endpoint = context.getEndpoint("sftp://localhost/inbox?readLock=" + readLock, SftpEndpoint.class);
        return ((GenericFileProcessStrategySupport<?>) endpoint.createGenericFileStrategy()).getExclusiveReadLockStrategy();
    }
}
