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
package org.apache.camel.component.cm;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class CMConfiguration {

    @UriParam @Metadata(required = "true")
    @NotNull
    private String productToken;
    @UriParam
    @NotNull @Size(min = 1, max = 11)
    private String defaultFrom;
    @UriParam(defaultValue = "8")
    @Min(1) @Max(8)
    private int defaultMaxNumberOfParts = 8;
    @UriParam
    private boolean testConnectionOnStartup;

    public String getProductToken() {
        return productToken;
    }

    /**
     * The unique token to use
     */
    public void setProductToken(String productToken) {
        this.productToken = productToken;
    }

    public String getDefaultFrom() {
        return defaultFrom;
    }

    /**
     * This is the sender name. The maximum length is 11 characters.
     */
    public void setDefaultFrom(final String defaultFrom) {
        this.defaultFrom = defaultFrom;
    }

    public int getDefaultMaxNumberOfParts() {
        return defaultMaxNumberOfParts;
    }

    /**
     * If it is a multipart message forces the max number. Message can be truncated.
     * Technically the gateway will first check if a message is larger than 160 characters,
     * if so, the message will be cut into multiple 153 characters parts limited by these parameters.
     */
    public void setDefaultMaxNumberOfParts(final int defaultMaxNumberOfParts) {
        this.defaultMaxNumberOfParts = defaultMaxNumberOfParts;
    }

    public boolean isTestConnectionOnStartup() {
        return testConnectionOnStartup;
    }

    /**
     * Whether to test the connection to the SMS Gateway on startup
     */
    public void setTestConnectionOnStartup(final boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

}
