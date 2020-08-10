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
package org.apache.camel.component.minio;

import java.time.ZonedDateTime;

import io.minio.ServerSideEncryption;
import io.minio.ServerSideEncryptionCustomerKey;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public final class MinioChecks {
    private MinioChecks() {
        // Prevent instantiation of this factory class.
        throw new RuntimeException("Do not instantiate a Factory class! Refer to the class to learn how to properly use this factory implementation.");
    }

    static void checkServerSideEncryptionConfig(final MinioConfiguration configuration, final java.util.function.Consumer<ServerSideEncryption> fn) {
        if (isNotEmpty(configuration.getServerSideEncryption())) {
            fn.accept(configuration.getServerSideEncryption());
        }
    }

    static void checkServerSideEncryptionCustomerKeyConfig(final MinioConfiguration configuration, final java.util.function.Consumer<ServerSideEncryptionCustomerKey> fn) {
        if (isNotEmpty(configuration.getServerSideEncryptionCustomerKey())) {
            fn.accept(configuration.getServerSideEncryptionCustomerKey());
        }
    }

    static void checkOffsetConfig(final MinioConfiguration configuration, final java.util.function.Consumer<Long> fn) {
        if (configuration.getOffset() > 0) {
            fn.accept(configuration.getOffset());
        }
    }

    static void checkLengthConfig(final MinioConfiguration configuration, final java.util.function.Consumer<Long> fn) {
        if (configuration.getLength() > 0) {
            fn.accept(configuration.getLength());
        }
    }

    static void checkVersionIdConfig(final MinioConfiguration configuration, final java.util.function.Consumer<String> fn) {
        if (isNotEmpty(configuration.getVersionId())) {
            fn.accept(configuration.getVersionId());
        }
    }

    static void checkMatchETagConfig(final MinioConfiguration configuration, final java.util.function.Consumer<String> fn) {
        if (isNotEmpty(configuration.getMatchETag())) {
            fn.accept(configuration.getMatchETag());
        }
    }

    static void checkNotMatchETagConfig(final MinioConfiguration configuration, final java.util.function.Consumer<String> fn) {
        if (isNotEmpty(configuration.getNotMatchETag())) {
            fn.accept(configuration.getNotMatchETag());
        }
    }

    static void checkModifiedSinceConfig(final MinioConfiguration configuration, final java.util.function.Consumer<ZonedDateTime> fn) {
        if (isNotEmpty(configuration.getModifiedSince())) {
            fn.accept(configuration.getModifiedSince());
        }
    }

    static void checkUnModifiedSinceConfig(final MinioConfiguration configuration, final java.util.function.Consumer<ZonedDateTime> fn) {
        if (isNotEmpty(configuration.getUnModifiedSince())) {
            fn.accept(configuration.getUnModifiedSince());
        }
    }
}
