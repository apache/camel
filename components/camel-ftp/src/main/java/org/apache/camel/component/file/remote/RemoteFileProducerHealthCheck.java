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
package org.apache.camel.component.file.remote;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

/**
 * FTP producer readiness health-check
 */
public class RemoteFileProducerHealthCheck extends AbstractHealthCheck {

    private final RemoteFileProducer<?> producer;

    public RemoteFileProducerHealthCheck(RemoteFileProducer<?> producer) {
        super("camel", "producer:ftp-" + producer.getEndpoint().getConfiguration().getHost());
        this.producer = producer;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        Exchange dummy = producer.createExchange();
        Exception cause = null;
        try {
            producer.doPreWriteCheck(dummy, true);
        } catch (Exception e) {
            cause = e;
        }
        if (cause != null) {
            builder.down();
            builder.message("FtpProducer is not ready");
            builder.detail("serviceUrl", producer.getEndpoint().getServiceUrl());
            builder.error(cause);
            if (cause instanceof GenericFileOperationFailedException gfe) {
                int code = gfe.getCode();
                String msg = gfe.getReason();
                if (code > 0 && msg != null) {
                    builder.detail("ftp.code", code);
                    builder.detail("ftp.reason", msg.trim());
                }
            }
        } else {
            builder.up();
        }
    }
}
