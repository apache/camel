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
package org.apache.camel.component.azure.storage.blob;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobStorageException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperations;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;

public class BlobConsumer extends ScheduledPollConsumer {

    public BlobConsumer(final BlobEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        final String containerName = getEndpoint().getConfiguration().getContainerName();
        final String blobName = getEndpoint().getConfiguration().getBlobName();
        final BlobServiceClient serviceClient = getEndpoint().getBlobServiceClient();
        final BlobClientWrapper clientWrapper = new BlobClientWrapper(serviceClient.getBlobContainerClient(containerName).getBlobClient(blobName));
        final BlobOperations operations = new BlobOperations(getEndpoint().getConfiguration(), clientWrapper);
        final Exchange exchange = getEndpoint().createExchange();

        try {
            BlobOperationResponse response;
            if (!ObjectHelper.isEmpty(getEndpoint().getConfiguration().getFileDir())) {
                // if we have a fileDir set, we download our content
                response = operations.downloadBlobToFile(exchange);
            } else {
                // otherwise, we rely on the outputstream/inputstream
                response = operations.getBlob(exchange);
            }

            getEndpoint().setResponseOnExchange(response, exchange);

            getAsyncProcessor().process(exchange);
            return 1;
        } catch (BlobStorageException ex) {
            if (404 == ex.getStatusCode()) {
                return 0;
            } else {
                throw ex;
            }
        }
    }

    @Override
    public BlobEndpoint getEndpoint() {
        return (BlobEndpoint) super.getEndpoint();
    }
}
