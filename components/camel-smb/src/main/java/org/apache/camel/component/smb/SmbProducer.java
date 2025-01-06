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

package org.apache.camel.component.smb;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SMB file producer
 */
public class SmbProducer extends GenericFileProducer<FileIdBothDirectoryInformation> {

    private static final Logger LOG = LoggerFactory.getLogger(SmbProducer.class);

    protected SmbProducer(final SmbEndpoint endpoint, GenericFileOperations<FileIdBothDirectoryInformation> operations) {
        super(endpoint, operations);
    }

    @Override
    public SmbEndpoint getEndpoint() {
        return (SmbEndpoint) super.getEndpoint();
    }

    @Override
    public void postWriteCheck(Exchange exchange) {
        try {
            boolean isLast = exchange.getProperty(ExchangePropertyKey.BATCH_COMPLETE, false, Boolean.class);
            if (isLast && getEndpoint().getConfiguration().isDisconnectOnBatchComplete()) {
                LOG.trace("postWriteCheck disconnect on batch complete from: {}", getEndpoint());
                getOperations().disconnect();
            }
            if (getEndpoint().getConfiguration().isDisconnect()) {
                LOG.trace("postWriteCheck disconnect from: {}", getEndpoint());
                getOperations().disconnect();
            }
        } catch (GenericFileOperationFailedException e) {
            // ignore just log a warning
            LOG.warn("Exception occurred during disconnecting from: {} {}. This exception is ignored.", getEndpoint(),
                    e.getMessage());
        }
    }

    private SmbOperations getOperations() {
        return (SmbOperations) operations;
    }

}
