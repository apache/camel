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
package org.apache.camel.support;

import org.apache.camel.spi.Language;

/**
 * Base class for {@link Language} implementations that support different sources of input data.
 */
public abstract class SingleInputLanguageSupport extends LanguageSupport {

    /**
     * Name of header to use as input, instead of the message body
     */
    private String headerName;
    /**
     * Name of property to use as input, instead of the message body.
     * <p>
     * It has a lower precedent than the name of header if both are set.
     */
    private String propertyName;

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Name of property to use as input, instead of the message body.
     * <p>
     * It has a lower precedent than the name of header if both are set.
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }
}
