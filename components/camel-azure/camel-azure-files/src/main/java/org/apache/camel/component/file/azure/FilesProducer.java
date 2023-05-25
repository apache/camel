package org.apache.camel.component.file.azure;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.RemoteFileProducer;

public class FilesProducer<T> extends RemoteFileProducer<T> {

    FilesProducer(RemoteFileEndpoint<T> endpoint, RemoteFileOperations<T> operations) {
        super(endpoint, operations);
    }

    @Override
    public void preWriteCheck(Exchange exchange) throws Exception {
        // noop
    }
}
