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
package org.apache.camel.component.sparkrest;

import org.apache.camel.impl.DefaultHeaderFilterStrategy;

/**
 * Default Spark {@link org.apache.camel.spi.HeaderFilterStrategy} used when binding with {@link org.apache.camel.component.sparkrest.SparkBinding}.
 */
public class SparkHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public SparkHeaderFilterStrategy() {
        initialize();
    }

    protected void initialize() {
        getInFilter().add("content-type");
        
        getOutFilter().add("content-length");
        getOutFilter().add("content-type");
        getOutFilter().add("host");
        getOutFilter().add("user-agent");
        // Add the filter for the Generic Message header
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.5
        getOutFilter().add("cache-control");
        getOutFilter().add("connection");
        getOutFilter().add("date");
        getOutFilter().add("pragma");
        getOutFilter().add("trailer");
        getOutFilter().add("transfer-encoding");
        getOutFilter().add("upgrade");
        getOutFilter().add("via");
        getOutFilter().add("warning");

        setLowerCase(true);

        // filter headers begin with "Camel" or "org.apache.camel"
        // must ignore case for Http based transports
        setOutFilterPattern("(?i)(Camel|org\\.apache\\.camel)[\\.|a-z|A-z|0-9]*");

        // filter out splat as its an internal header
        getOutFilter().add(SparkConstants.SPLAT);
    }

}
