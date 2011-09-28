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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.apache.camel.Exchange;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcloudsBlobStoreProducer extends JcloudsProducer {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsBlobStoreProducer.class);

    private BlobStore blobStore;

    public JcloudsBlobStoreProducer(JcloudsEndpoint endpoint, BlobStore blobStore) {
        super(endpoint);
        this.blobStore = blobStore;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String container = getContainerName(exchange);
        String blobName = getBlobName(exchange);
        String operation = getOperation(exchange);

        LOG.trace("Processing {} operation on '{}'", operation, container + "/" + blobName);
        Object body = exchange.getIn().getBody();
        if (JcloudsConstants.GET.equals(operation)) {
            exchange.getOut().setBody(JcloudsBlobStoreHelper.readBlob(blobStore, container, blobName, Thread.currentThread().getContextClassLoader()));
        } else {
            JcloudsBlobStoreHelper.writeBlob(blobStore, container, blobName, body);
        }
    }

    /**
     * Retrieves the blobName from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    protected String getBlobName(Exchange exchange) {
        String blobName = ((JcloudsBlobStoreEndpoint) getEndpoint()).getBlobName();
        if (exchange.getIn().getHeader(JcloudsConstants.BLOB_NAME) != null) {
            blobName = (String) exchange.getIn().getHeader(JcloudsConstants.BLOB_NAME);
        }
        return blobName;
    }

    /**
     * Retrieves the containerName from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    protected String getContainerName(Exchange exchange) {
        String containerName = ((JcloudsBlobStoreEndpoint) getEndpoint()).getContainer();
        if (exchange.getIn().getHeader(JcloudsConstants.CONTAINER_NAME) != null) {
            containerName = (String) exchange.getIn().getHeader(JcloudsConstants.CONTAINER_NAME);
        }
        return containerName;
    }

    /**
     * Retrieves the operation from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    public String getOperation(Exchange exchange) {
        String operation = ((JcloudsBlobStoreEndpoint) getEndpoint()).getOperation();

        if (exchange.getIn().getHeader(JcloudsConstants.OPERATION) != null) {
            operation = (String) exchange.getIn().getHeader(JcloudsConstants.OPERATION);
        }
        return operation;
    }
}
