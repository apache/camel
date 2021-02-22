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
package org.apache.camel.component.google.storage.localstorage;

import com.google.cloud.spi.ServiceRpcFactory;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.spi.v1.StorageRpc;

//this class has been extended from
//https://github.com/googleapis/java-storage-nio/blob/master/google-cloud-nio/src/main/java/com/google/cloud/storage/contrib/nio/testing/LocalStorageHelper.java
/**
 * Utility to create an in-memory storage configuration for testing. Storage options can be obtained via the
 * {@link #getOptions()} method. Returned options will point to FakeStorageRpc.
 *
 * <p>
 * Note, the created in-memory storage configuration supports limited set of operations and is <b>not</b> thread-safe:
 *
 * <ul>
 * <li>Supported operations
 * <ul>
 * <li>object create
 * <li>object get
 * <li>object delete
 * <li>list the contents of a bucket
 * <li>bucket create (now supported)
 * <li>bucket get (now supported)
 * <li>bucket delete (now supported)
 * <li>list all buckets (now supported)
 * </ul>
 * <li>Unsupported operations
 * <ul>
 * <li>generations
 * <li>file attributes
 * <li>patch
 * <li>continueRewrite
 * <li>createBatch
 * <li>checksums, etags
 * <li>IAM operations
 * </ul>
 * </ul>
 *
 * {@link FakeStorageRpc#list(String, java.util.Map)} lists all the objects that have been created rather than the
 * objects in the provided bucket. Since this class does not support creating, listing and deleting buckets, the
 * parameter bucket here is not actually used and on serves as a placeholder.
 */
public final class LocalStorageHelper {

    // used for testing. Will throw if you pass it an option.
    private static final FakeStorageRpc INSTANCE = new FakeStorageRpc(true);

    private LocalStorageHelper() {
    }

    /**
     * Returns a {@link StorageOptions} that use the static FakeStorageRpc instance, and resets it first so you start
     * from a clean slate. That instance will throw if you pass it any option.
     */
    public static StorageOptions getOptions() {
        INSTANCE.reset();
        return StorageOptions.newBuilder()
                .setProjectId("dummy-project-for-testing")
                .setServiceRpcFactory(
                        new ServiceRpcFactory<StorageOptions>() {
                            @Override
                            public StorageRpc create(StorageOptions options) {
                                return INSTANCE;
                            }
                        })
                .build();
    }

    /**
     * Returns a {@link StorageOptions} that creates a new FakeStorageRpc instance with the given option.
     */
    public static StorageOptions customOptions(final boolean throwIfOptions) {
        return StorageOptions.newBuilder()
                .setProjectId("dummy-project-for-testing")
                .setServiceRpcFactory(
                        new ServiceRpcFactory<StorageOptions>() {
                            @Override
                            public StorageRpc create(StorageOptions options) {
                                return new FakeStorageRpc(throwIfOptions);
                            }
                        })
                .build();
    }

}
