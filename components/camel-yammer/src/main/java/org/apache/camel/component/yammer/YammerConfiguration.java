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
package org.apache.camel.component.yammer;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class YammerConfiguration {

    @UriPath(name = "function") @Metadata(required = "true")
    private YammerFunctionType functionType;
    private String function;
    @UriParam
    private String consumerKey;
    @UriParam
    private String consumerSecret;
    @UriParam
    private String accessToken;
    @UriParam
    private boolean useJson;
    @UriParam(defaultValue = "5000")
    private long delay = 3000 + 2000; // 3 sec per poll is enforced by yammer; add 2 sec for safety
    @UriParam(defaultValue = "-1")
    private int limit = -1; // default is unlimited
    @UriParam(defaultValue = "-1")
    private int olderThan = -1;
    @UriParam(defaultValue = "-1")
    private int newerThan = -1;
    @UriParam
    private String threaded;
    @UriParam
    private String userId;
    private ApiRequestor requestor;

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public YammerFunctionType getFunctionType() {
        return functionType;
    }

    public void setFunctionType(YammerFunctionType functionType) {
        this.functionType = functionType;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public boolean isUseJson() {
        return useJson;
    }

    public void setUseJson(boolean useJson) {
        this.useJson = useJson;
    }

    public ApiRequestor getRequestor(String apiUrl) throws Exception {
        if (requestor == null) {
            requestor = new ScribeApiRequestor(apiUrl, getAccessToken()); 
        }
        return requestor;
    }

    public void setRequestor(ApiRequestor requestor) {
        this.requestor = requestor;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOlderThan() {
        return olderThan;
    }

    public void setOlderThan(int olderThan) {
        this.olderThan = olderThan;
    }

    public int getNewerThan() {
        return newerThan;
    }

    public void setNewerThan(int newerThan) {
        this.newerThan = newerThan;
    }

    public String getThreaded() {
        return threaded;
    }

    public void setThreaded(String threaded) {
        this.threaded = threaded;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

}
