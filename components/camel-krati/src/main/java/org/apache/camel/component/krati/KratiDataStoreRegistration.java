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

import java.io.IOException;
import krati.store.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KratiDataStoreRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(KratiDataStoreRegistration.class);

    private final DataStore<Object, Object> dataStore;
    private int registrationCount;

    public KratiDataStoreRegistration(DataStore<Object, Object> dataStore) {
        this.dataStore = dataStore;
    }

    public int register() {
        return ++registrationCount;
    }

    public boolean unregister() {
        if (--registrationCount <= 0) {
            try {
                dataStore.close();
            } catch (IOException e) {
                LOG.warn("Error while closing datastore. This exception is ignored.", e);
            }
            return true;
        } else {
            return false;
        }
    }

    public DataStore<Object, Object> getDataStore() {
        register();
        return dataStore;
    }
}
