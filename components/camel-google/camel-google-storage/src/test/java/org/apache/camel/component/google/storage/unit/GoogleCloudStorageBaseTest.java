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
package org.apache.camel.component.google.storage.unit;

import com.google.cloud.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.storage.GoogleCloudStorageComponent;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.test.junit5.CamelTestSupport;

public abstract class GoogleCloudStorageBaseTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);

        Storage storage = createStorage();
        initStorage(storage);
        component.getConfiguration().setStorageClient(storage);
        return context;
    }

    private Storage createStorage() {
        return LocalStorageHelper.getOptions().getService();
    }

    private void initStorage(Storage storage) {
        //override if you want to inizialite the storage
    }
}
