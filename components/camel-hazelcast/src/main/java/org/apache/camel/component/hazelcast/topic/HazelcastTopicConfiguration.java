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
package org.apache.camel.component.hazelcast.topic;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Hazelcast Topic Component configuration.
 */
@UriParams
public class HazelcastTopicConfiguration {

    @UriParam(label = "common", defaultValue = "false")
    private boolean reliable;

    /**
     * Define if the endpoint will use a reliable Topic struct or not.
     */
    public boolean isReliable() {
        return reliable;
    }

    public void setReliable(boolean reliable) {
        this.reliable = reliable;
    }
}
