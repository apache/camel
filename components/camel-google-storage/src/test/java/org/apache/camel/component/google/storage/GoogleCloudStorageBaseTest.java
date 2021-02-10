package org.apache.camel.component.google.storage;

import com.google.cloud.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.test.junit5.CamelTestSupport;

public class GoogleCloudStorageBaseTest extends CamelTestSupport {

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
