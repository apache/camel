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
package org.apache.camel.component.file;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.spi.SynchronizationVetoable;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenericFileOnCompletionHandoverTest {

    @Mock
    private GenericFileEndpoint<Object> endpoint;

    @Mock
    private GenericFileOperations<Object> operations;

    @Mock
    private GenericFileProcessStrategy<Object> processStrategy;

    private CamelContext context;
    private GenericFile<Object> testFile;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        testFile = genericFile();
        when(endpoint.getCamelContext()).thenReturn(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        context.stop();
    }

    @Test
    void implementsSynchronizationVetoable() {
        GenericFileOnCompletion<Object> completion
                = new GenericFileOnCompletion<>(endpoint, operations, processStrategy, testFile, "/data/file.txt");
        assertInstanceOf(SynchronizationVetoable.class, completion);
    }

    @Test
    void allowHandoverWhenStreamDownloadEnabled() {
        assertTrue(createCompletion(true).allowHandover());
    }

    @Test
    void denyHandoverWhenStreamDownloadDisabled() {
        assertFalse(createCompletion(false).allowHandover());
    }

    @Test
    void unitOfWorkHandoversCompletionWhenStreamDownload() throws Exception {
        when(endpoint.isIdempotent()).thenReturn(false);
        when(endpoint.getInProgressRepository()).thenReturn(new MemoryIdempotentRepository());

        Exchange source = new DefaultExchange(context);
        DefaultUnitOfWork sourceUow = createUnitOfWork(source);
        source.getExchangeExtension().setUnitOfWork(sourceUow);

        GenericFileOnCompletion<Object> completion = createCompletion(true);
        source.getExchangeExtension().addOnCompletion(completion);

        Exchange target = new DefaultExchange(context);
        DefaultUnitOfWork targetUow = createUnitOfWork(target);
        target.getExchangeExtension().setUnitOfWork(targetUow);

        sourceUow.handoverSynchronization(target);

        sourceUow.done(source);
        verify(processStrategy, never()).commit(operations, endpoint, source, testFile);

        targetUow.done(target);
        verify(processStrategy).commit(operations, endpoint, target, testFile);
    }

    @Test
    void unitOfWorkDoesNotHandoverCompletionWhenNotStreamDownload() throws Exception {
        when(endpoint.isIdempotent()).thenReturn(false);
        when(endpoint.getInProgressRepository()).thenReturn(new MemoryIdempotentRepository());

        Exchange source = new DefaultExchange(context);
        DefaultUnitOfWork sourceUow = createUnitOfWork(source);
        source.getExchangeExtension().setUnitOfWork(sourceUow);

        GenericFileOnCompletion<Object> completion = createCompletion(false);
        source.getExchangeExtension().addOnCompletion(completion);

        Exchange target = new DefaultExchange(context);
        DefaultUnitOfWork targetUow = createUnitOfWork(target);
        target.getExchangeExtension().setUnitOfWork(targetUow);

        sourceUow.handoverSynchronization(target);

        sourceUow.done(source);
        verify(processStrategy).commit(operations, endpoint, source, testFile);
        verify(processStrategy, never()).commit(operations, endpoint, target, testFile);
    }

    @Test
    void correlatedCopyHandoversStreamDownloadCompletion() throws Exception {
        when(endpoint.isIdempotent()).thenReturn(false);
        when(endpoint.getInProgressRepository()).thenReturn(new MemoryIdempotentRepository());

        Exchange source = new DefaultExchange(context);
        DefaultUnitOfWork sourceUow = createUnitOfWork(source);
        source.getExchangeExtension().setUnitOfWork(sourceUow);
        source.getExchangeExtension().addOnCompletion(createCompletion(true));

        Exchange copy = ExchangeHelper.createCorrelatedCopy(source, true, false);
        DefaultUnitOfWork copyUow = createUnitOfWork(copy);
        copy.getExchangeExtension().setUnitOfWork(copyUow);

        sourceUow.done(source);
        verify(processStrategy, never()).commit(operations, endpoint, source, testFile);

        copyUow.done(copy);
        verify(processStrategy).commit(operations, endpoint, copy, testFile);
        assertSame(source.getExchangeId(), copy.getProperty(ExchangePropertyKey.CORRELATION_ID));
    }

    private DefaultUnitOfWork createUnitOfWork(Exchange exchange) {
        return new DefaultUnitOfWork(exchange);
    }

    private GenericFileOnCompletion<Object> createCompletion(boolean streamDownload) {
        when(endpoint.isStreamDownload()).thenReturn(streamDownload);
        return new GenericFileOnCompletion<>(endpoint, operations, processStrategy, testFile, "/data/file.txt");
    }

    private GenericFile<Object> genericFile() {
        GenericFile<Object> file = new GenericFile<>();
        file.setFileName("file.txt");
        file.setAbsolute(true);
        file.setAbsoluteFilePath("/data/file.txt");
        return file;
    }
}
