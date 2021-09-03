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
package org.apache.camel.component.vertx.kafka.configuration;

import org.apache.camel.Exchange;
import org.apache.camel.component.vertx.kafka.VertxKafkaHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriParam;

public abstract class BaseVertxKafkaConfiguration implements HeaderFilterStrategyAware {

    @UriParam(label = "common")
    private HeaderFilterStrategy headerFilterStrategy = new VertxKafkaHeaderFilterStrategy();
    @UriParam(label = "consumer")
    private boolean allowManualCommit;

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    /**
     * Whether to allow doing manual commits via
     * {@link org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommit}.
     * <p/>
     * If this option is enabled then an instance of
     * {@link org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommit} is stored on the {@link Exchange}
     * message header, which allows end users to access this API and perform manual offset commits via the Kafka
     * consumer.
     *
     * Note: To take full control of the offset committing, you may need to disable the Kafka Consumer default auto
     * commit behavior by setting 'enableAutoCommit' to 'false'.
     */
    public boolean isAllowManualCommit() {
        return allowManualCommit;
    }

    public void setAllowManualCommit(boolean allowManualCommit) {
        this.allowManualCommit = allowManualCommit;
    }
}
