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
package org.apache.camel.builder;

import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.errorhandler.DeadLetterChannelConfiguration;
import org.apache.camel.model.errorhandler.DeadLetterChannelProperties;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerConfiguration;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.spi.CamelLogger;
import org.slf4j.LoggerFactory;

/**
 * A builder of a <a href="http://camel.apache.org/dead-letter-channel.html">Dead Letter Channel</a>
 */
public class DeadLetterChannelBuilder extends DefaultErrorHandlerBuilder implements DeadLetterChannelProperties {

    public DeadLetterChannelBuilder() {
        // no-arg constructor used by Spring DSL
    }

    public DeadLetterChannelBuilder(Endpoint deadLetter) {
        setDeadLetterUri(deadLetter.getEndpointUri());
        // DLC do not log exhausted by default
        getRedeliveryPolicy().setLogExhausted(false);
    }

    public DeadLetterChannelBuilder(String uri) {
        setDeadLetterUri(uri);
    }

    @Override
    DefaultErrorHandlerConfiguration createConfiguration() {
        return new DeadLetterChannelConfiguration();
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        DeadLetterChannelBuilder answer = new DeadLetterChannelBuilder();
        super.cloneBuilder(answer);
        return answer;
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(DeadLetterChannel.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "DeadLetterChannelBuilder(" + getDeadLetterUri() + ")";
    }
}
