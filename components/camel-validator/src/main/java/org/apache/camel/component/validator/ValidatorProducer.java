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
package org.apache.camel.component.validator;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.processor.validation.ValidatingProcessor;
import org.apache.camel.support.service.ServiceHelper;

public class ValidatorProducer extends DefaultAsyncProducer {

    private final ValidatingProcessor validatingProcessor;

    public ValidatorProducer(Endpoint endpoint, ValidatingProcessor validatingProcessor) {
        super(endpoint);
        this.validatingProcessor = validatingProcessor;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        return validatingProcessor.process(exchange, callback);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(validatingProcessor);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(validatingProcessor);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doStop();
        ServiceHelper.stopAndShutdownService(validatingProcessor);
    }
}
