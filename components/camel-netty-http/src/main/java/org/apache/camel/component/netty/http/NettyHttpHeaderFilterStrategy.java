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
package org.apache.camel.component.netty.http;

import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.http.HttpUtil;

/**
 * Default Netty {@link HeaderFilterStrategy} used when binding with {@link NettyHttpBinding}.
 */
public class NettyHttpHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public NettyHttpHeaderFilterStrategy() {
        initialize();
    }

    protected void initialize() {
        HttpUtil.addCommonFilters(getOutFilter());

        setLowerCase(true);

        // filter headers begin with "Camel" or "org.apache.camel"
        // must ignore case for Http based transports
        setOutFilterStartsWith(CAMEL_FILTER_STARTS_WITH);
        setInFilterStartsWith(CAMEL_FILTER_STARTS_WITH);
    }
}
