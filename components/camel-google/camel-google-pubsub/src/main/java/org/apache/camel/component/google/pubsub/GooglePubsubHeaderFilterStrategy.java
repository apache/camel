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

package org.apache.camel.component.google.pubsub;

import org.apache.camel.support.DefaultHeaderFilterStrategy;

public class GooglePubsubHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public GooglePubsubHeaderFilterStrategy() {
        this(false);
    }

    public GooglePubsubHeaderFilterStrategy(boolean includeAllGoogleProperties) {
        setOutFilterStartsWith(DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH);
        setInFilterStartsWith(DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH);
        getOutFilter().add("authorization");
        if (!includeAllGoogleProperties) {
            ignoreGoogProperties();
        }
    }

    protected void ignoreGoogProperties() {
        String[] filterStartWith = new String[DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH.length + 3];
        System.arraycopy(
                DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH,
                0,
                filterStartWith,
                0,
                DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH.length);
        filterStartWith[DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH.length] = "x-goog";
        filterStartWith[DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH.length + 1] = "X-GOOG";
        filterStartWith[DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH.length + 2] = "goog";
        setOutFilterStartsWith(filterStartWith);
        setInFilterStartsWith(filterStartWith);
        getOutFilter().add("grpc-timeout");
    }
}
