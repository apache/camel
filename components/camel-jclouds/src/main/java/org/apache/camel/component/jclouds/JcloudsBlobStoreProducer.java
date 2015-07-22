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
package org.apache.camel.component.jclouds;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.io.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcloudsBlobStoreProducer extends JcloudsProducer {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsBlobStoreProducer.class);

    private final JcloudsBlobStoreEndpoint endpoint;
    private BlobStore blobStore;

    public JcloudsBlobStoreProducer(JcloudsBlobStoreEndpoint endpoint, BlobStore blobStore) {
        super(endpoint);
        this.blobStore = blobStore;
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String container = endpoint.getContainer();
        String locationId = endpoint.getLocationId();
        JcloudsBlobStoreHelper.ensureContainerExists(blobStore, container, locationId);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String container = getContainerName(exchange);
        String blobName = getBlobName(exchange);
        String operation = getOperation(exchange);
        List blobNames = getBlobNameList(exchange);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing {} operation on '{}'", operation, container + "/" + blobName);
        }
        if (JcloudsConstants.GET.equals(operation)) {
            exchange.getOut().setBody(JcloudsBlobStoreHelper.readBlob(blobStore, container, blobName));
        } else if (JcloudsConstants.COUNT_BLOBS.equals(operation)) {
            exchange.getOut().setBody(JcloudsBlobStoreHelper.countBlob(blobStore, container));
        } else if (JcloudsConstants.REMOVE_BLOB.equals(operation)) {
            JcloudsBlobStoreHelper.removeBlob(blobStore, container, blobName);
        } else if (JcloudsConstants.CLEAR_CONTAINER.equals(operation)) {
            JcloudsBlobStoreHelper.clearContainer(blobStore, container);
        } else if (JcloudsConstants.DELETE_CONTAINER.equals(operation)) {
            JcloudsBlobStoreHelper.deleteContainer(blobStore, container);
        } else if (JcloudsConstants.CONTAINER_EXISTS.equals(operation)) {
            exchange.getOut().setBody(JcloudsBlobStoreHelper.containerExists(blobStore, container));
        } else if (JcloudsConstants.REMOVE_BLOBS.equals(operation)) {
            JcloudsBlobStoreHelper.removeBlobs(blobStore, container, blobNames);
        } else {
            Payload body = exchange.getIn().getBody(Payload.class);
            JcloudsBlobStoreHelper.writeBlob(blobStore, container, blobName, body);
        }
    }

    /**
     * Retrieves the blobName from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    protected String getBlobName(Exchange exchange) {
        String blobName = ((JcloudsBlobStoreEndpoint) getEndpoint()).getBlobName();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(JcloudsConstants.BLOB_NAME))) {
            blobName = exchange.getIn().getHeader(JcloudsConstants.BLOB_NAME, String.class);
        }
        return blobName;
    }

    /**
     * Retrieves the containerName from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    protected String getContainerName(Exchange exchange) {
        String containerName = ((JcloudsBlobStoreEndpoint) getEndpoint()).getContainer();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(JcloudsConstants.CONTAINER_NAME))) {
            containerName = exchange.getIn().getHeader(JcloudsConstants.CONTAINER_NAME, String.class);
        }
        return containerName;
    }

    /**
     * Retrieves the operation from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    public String getOperation(Exchange exchange) {
        String operation = ((JcloudsBlobStoreEndpoint) getEndpoint()).getOperation();

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(JcloudsConstants.OPERATION))) {
            operation = exchange.getIn().getHeader(JcloudsConstants.OPERATION, String.class);
        }
        return operation;
    }

    /**
     * Retrieves the locationId from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    public String getLocationId(Exchange exchange) {
        String operation = ((JcloudsBlobStoreEndpoint) getEndpoint()).getLocationId();

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(JcloudsConstants.LOCATION_ID))) {
            operation = exchange.getIn().getHeader(JcloudsConstants.LOCATION_ID, String.class);
        }
        return operation;
    }
    
    /**
     * Retrieves the Blob name list from the exchange headers.
     */
    public List getBlobNameList(Exchange exchange) {
        List blobNames = new ArrayList<>();

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(JcloudsConstants.BLOB_NAME_LIST))) {
            blobNames = exchange.getIn().getHeader(JcloudsConstants.BLOB_NAME_LIST, List.class);
        }
        return blobNames;
    }
}
