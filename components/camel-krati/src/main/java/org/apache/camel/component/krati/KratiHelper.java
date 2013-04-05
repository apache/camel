/**
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
package org.apache.camel.component.krati;

import java.io.File;

import krati.core.StoreConfig;
import krati.core.segment.SegmentFactory;
import krati.io.Serializer;
import krati.store.DataSet;
import krati.store.DataStore;
import krati.store.DynamicDataSet;
import krati.store.DynamicDataStore;
import krati.store.SerializableObjectStore;
import krati.util.HashFunction;

import org.apache.camel.RuntimeCamelException;

public final class KratiHelper {

    private KratiHelper() {
        //Utillity Class
    }

    /**
     * Creates a {@link krati.store.SerializableObjectStore} with the given parameters.
     *
     * @param path            The directory which the store will use.
     * @param initialCapacity
     * @param segmentFileSize
     * @param segmentFactory  The segment factory, defaults to {@link krati.core.segment.ChannelSegmentFactory}.
     * @param hashFunction    The hash function, defaults to {@link krati.util.FnvHashFunction}.
     * @param keySerializer   The serializer used for keys, defaults to {@link org.apache.camel.component.krati.serializer.KratiDefaultSerializer}.
     * @param valueSerializer The serializer used for values,defaults to {@link org.apache.camel.component.krati.serializer.KratiDefaultSerializer}.
     * @return
     */
    public static <K, V> DataStore<K, V> createDataStore(String path, int initialCapacity, int segmentFileSize, SegmentFactory segmentFactory,
                                            HashFunction<byte[]> hashFunction, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        DataStore<K, V> result = null;
        File homeDir = new File(path);
        homeDir.mkdirs();
        try {
            StoreConfig storeConfig = new StoreConfig(homeDir, initialCapacity);
            storeConfig.setSegmentFactory(segmentFactory);
            storeConfig.setHashFunction(hashFunction);
            storeConfig.setSegmentFileSizeMB(segmentFileSize);
            DataStore<byte[], byte[]> dynamicDataStore = new DynamicDataStore(storeConfig);
            result = new SerializableObjectStore<K, V>(dynamicDataStore, keySerializer, valueSerializer);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to create Krati DataStore.", e);
        }
        return result;
    }


    /**
     * Creates a {@link krati.store.DynamicDataSet} with the given parameters.
     *
     * @param path            The directory which the set will use.
     * @param initialCapacity
     * @param segmentFactory  The segment factory, defaults to {@link krati.core.segment.ChannelSegmentFactory}.
     * @return
     */
    public static DataSet<byte[]> createDataSet(String path, int initialCapacity, SegmentFactory segmentFactory) {
        DataSet<byte[]> result = null;
        File homeDir = new File(path);
        homeDir.mkdirs();
        try {
            result = new DynamicDataSet(homeDir, initialCapacity, segmentFactory);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to create Krati DataSet. This exception is ignored.", e);
        }
        return result;
    }
}
