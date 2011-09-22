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
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcloudsBlobStoreProducer extends JcloudsProducer {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsBlobStoreProducer.class);

    private BlobStoreContext blobStoreContext;
    private String container;

    public JcloudsBlobStoreProducer(JcloudsEndpoint endpoint, BlobStoreContext blobStoreContext, String container) {
        super(endpoint);
        this.blobStoreContext = blobStoreContext;
        this.container = container;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String blobName = extractBlobName(exchange);
        if (blobName != null && exchange.getIn() != null && exchange.getIn().getBody() != null) {
            Object body = exchange.getIn().getBody();
            BlobStore blobStore = blobStoreContext.getBlobStore();
            Blob blob = blobStore.blobBuilder(blobName).build();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = null;
            try {
                oos = new ObjectOutputStream(baos);
                oos.writeObject(body);
                blob.setPayload(baos.toByteArray());
                blobStore.putBlob(container, blob);
            } catch (IOException e) {
                LOG.error("Error while writing blob", e);
            } finally {
                if (oos != null) {
                    try {
                        oos.close();
                    } catch (IOException e) {
                    }
                }

                if (baos != null) {
                    try {
                        baos.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

    }

    /**
     * Extracts the BLOB_NAME from Exchange headers / properties.
     *
     * @param exchange
     * @return
     */
    public String extractBlobName(Exchange exchange) {
        String blobName = null;
        if (exchange != null) {
            if (exchange.getProperty(JcloudsConstants.BLOB_NAME) != null) {
                blobName = (String) exchange.getProperty(JcloudsConstants.BLOB_NAME);
            } else if (exchange.getIn() != null && exchange.getIn().getHeader(JcloudsConstants.BLOB_NAME) != null) {
                blobName = (String) exchange.getIn().getHeader(JcloudsConstants.BLOB_NAME);
            }
        }
        return blobName;
    }
}
