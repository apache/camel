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
package org.apache.camel.component.infinispan.remote.protostream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.util.ClassLoadingAwareObjectInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for {@link DefaultExchangeHolder} and the Infinispan Protostream marshaller.
 */
final class DefaultExchangeHolderUtils {

    /**
     * Default deserialization filter that restricts which classes can be deserialized. Allows standard Java types and
     * Apache Camel types. Can be overridden via the JVM system property {@code jdk.serialFilter}.
     */
    static final String DEFAULT_DESERIALIZATION_FILTER = "java.**;javax.**;org.apache.camel.**;!*";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExchangeHolderUtils.class);

    private DefaultExchangeHolderUtils() {
        // Utility class
    }

    static byte[] serialize(DefaultExchangeHolder holder) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(holder);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static DefaultExchangeHolder deserialize(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ClassLoadingAwareObjectInputStream(bais)) {
            ObjectInputFilter jvmFilter = ObjectInputFilter.Config.getSerialFilter();
            if (jvmFilter != null) {
                ois.setObjectInputFilter(jvmFilter);
            } else {
                LOG.debug("No JVM-wide deserialization filter set, applying default Camel filter: {}",
                        DEFAULT_DESERIALIZATION_FILTER);
                ois.setObjectInputFilter(ObjectInputFilter.Config.createFilter(DEFAULT_DESERIALIZATION_FILTER));
            }
            return (DefaultExchangeHolder) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
