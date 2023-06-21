package org.apache.camel.component.file.azure;

import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.RemoteFileProducer;

public class FilesProducer extends RemoteFileProducer<ShareFileItem> {

    FilesProducer(FilesEndpoint endpoint, FilesOperations operations) {
        super(endpoint, operations);
    }

    @Override
    public void preWriteCheck(Exchange exchange) throws Exception {
        // noop
    }

    @Override
    public void start() {
        super.start();
        ((FilesOperations) operations).reconnectIfNecessary(null);
    }
}
