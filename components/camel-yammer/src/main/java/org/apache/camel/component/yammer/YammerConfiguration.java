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

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class YammerConfiguration {

    @UriParam
    private String consumerKey;
    @UriParam
    private String consumerSecret;
    @UriParam
    private String accessToken;
    
    private String function;
    
    @UriParam
    private boolean useJson;
    
    @UriParam
    private long delay = 3000 + 2000; // 3 sec per poll is enforced by yammer; add 2 sec for safety 
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

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getApiUrl() throws Exception {        
        switch (YammerFunctionType.fromUri(function)) {
        case MESSAGES:
            return String.format("%s%s.json", YammerConstants.YAMMER_BASE_API_URL, function);
        case ALGO:
        case FOLLOWING:
        case MY_FEED:
        case PRIVATE:
        case SENT:
            return String.format("%smessages/%s.json", YammerConstants.YAMMER_BASE_API_URL, function);
        default:
            throw new Exception(String.format("%s is not a valid Yammer function type.", function));
        }
    }

    public boolean isUseJson() {
        return useJson;
    }

    public void setUseJson(boolean useJson) {
        this.useJson = useJson;
    }

    public ApiRequestor getRequestor() throws Exception {
        if (requestor == null) {
            requestor = new ScribeApiRequestor(getApiUrl(), getAccessToken()); 
        }
        return requestor;
    }

    public void setRequestor(ApiRequestor requestor) {
        this.requestor = requestor;
    }

}
