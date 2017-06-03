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
package org.apache.camel.component.krati.processor.idempotent;

import krati.core.segment.ChannelSegmentFactory;
import krati.core.segment.SegmentFactory;
import krati.io.Serializer;
import krati.store.DataSet;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.component.krati.KratiHelper;
import org.apache.camel.component.krati.serializer.KratiDefaultSerializer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KratiIdempotentRepository extends ServiceSupport implements IdempotentRepository<String> {

    private static final Logger LOG = LoggerFactory.getLogger(KratiHelper.class);

    private String repositoryPath;

    private DataSet<byte[]> dataSet;
    private int initialCapacity = 100;
    private Serializer<String> serializer = new KratiDefaultSerializer<String>();
    private SegmentFactory segmentFactory = new ChannelSegmentFactory();

    public KratiIdempotentRepository(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String s) {
        byte[] bytes = serializer.serialize(s);
        try {
            synchronized (dataSet) {
                if (dataSet.has(bytes)) {
                    return false;
                } else {
                    return dataSet.add(serializer.serialize(s));
                }

            }
        } catch (Exception e) {
            LOG.warn("Error adding item to Krati idempotent repository. This exception is ignored.", e);
            return false;
        }
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String s) {
        byte[] bytes = serializer.serialize(s);
        try {
            return dataSet.has(bytes);
        } catch (Exception e) {
            LOG.warn("Error checking item exists in Krati idempotent repository. This exception is ignored.", e);
            return false;
        }
    }

    @Override
    @ManagedOperation(description = "Removes the given key from the store")
    public boolean remove(String s) {
        byte[] bytes = serializer.serialize(s);
        try {
            return dataSet.delete(bytes);
        } catch (Exception e) {
            LOG.warn("Error removing item from Krati idempotent repository. This exception is ignored.", e);
            return false;
        }
    }

    @Override
    public boolean confirm(String s) {
        return true;
    }
    
    @Override
    public void clear() {
        try {
            dataSet.clear();
        } catch (Exception e) {
            LOG.warn("Error clear Krati idempotent repository. This exception is ignored.", e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (dataSet == null) {
            this.dataSet = KratiHelper.createDataSet(repositoryPath, initialCapacity, segmentFactory);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (dataSet != null && dataSet.isOpen()) {
            this.dataSet.close();
        }
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    public DataSet<byte[]> getDataSet() {
        return dataSet;
    }

    public void setDataSet(DataSet<byte[]> dataSet) {
        this.dataSet = dataSet;
    }

    public int getInitialCapacity() {
        return initialCapacity;
    }

    public void setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    public Serializer<String> getSerializer() {
        return serializer;
    }

    public void setSerializer(Serializer<String> serializer) {
        this.serializer = serializer;
    }

    public SegmentFactory getSegmentFactory() {
        return segmentFactory;
    }

    public void setSegmentFactory(SegmentFactory segmentFactory) {
        this.segmentFactory = segmentFactory;
    }
}
