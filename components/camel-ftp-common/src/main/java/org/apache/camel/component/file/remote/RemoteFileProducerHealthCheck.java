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
    // what the last check of the connection found (UP before the first one); AbstractHealthCheck runs one call at a time
    private State lastState = State.UP;

    public RemoteFileProducerHealthCheck(RemoteFileProducer<?> producer) {
        super("camel", "producer:ftp-" + producer.getEndpoint().getConfiguration().getHost());
        this.producer = producer;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        Exchange dummy = producer.createExchange();
        Exception cause = null;
        boolean checked = true;
        try {
            // do not wait while the producer uses the connection, such as for an upload, which can last long
            checked = producer.getOperations().runIfIdle(() -> producer.doPreWriteCheck(dummy, true));
        } catch (Exception e) {
            cause = e;
        }
        if (!checked) {
            // report what the last check found, the connection is checked again when the producer no longer uses it
            builder.state(lastState);
            builder.message("FtpProducer is busy, the connection is in use");
            builder.detail("busy", true);
            return;
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
        lastState = builder.state();
    }
}
