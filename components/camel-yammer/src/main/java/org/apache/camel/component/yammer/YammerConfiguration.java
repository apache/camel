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
    @UriParam(label = "security") @Metadata(required = "true", secret = true)
    private String consumerKey;
    @UriParam(label = "security") @Metadata(required = "true", secret = true)
    private String consumerSecret;
    @UriParam(label = "security") @Metadata(required = "true")
    private String accessToken;
    @UriParam
    private boolean useJson;
    @UriParam(label = "consumer", defaultValue = "5000")
    private long delay = 3000 + 2000; // 3 sec per poll is enforced by yammer; add 2 sec for safety
    @UriParam(label = "consumer", defaultValue = "-1")
    private int limit = -1; // default is unlimited
    @UriParam(label = "consumer", defaultValue = "-1")
    private int olderThan = -1;
    @UriParam(label = "consumer", defaultValue = "-1")
    private int newerThan = -1;
    @UriParam(label = "consumer", enums = "true,extended")
    private String threaded;
    @UriParam(label = "consumer")
    private String userId;
    private ApiRequestor requestor;

    public String getConsumerKey() {
        return consumerKey;
    }

    /**
     * The consumer key
     */
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    /**
     * The consumer secret
     */
    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * Delay between polling in millis
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * The access token
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public YammerFunctionType getFunctionType() {
        return functionType;
    }

    /**
     * The function to use
     */
    public void setFunctionType(YammerFunctionType functionType) {
        this.functionType = functionType;
    }

    public String getFunction() {
        return function;
    }

    /**
     * The function to use
     */
    public void setFunction(String function) {
        this.function = function;
    }

    public boolean isUseJson() {
        return useJson;
    }

    /**
     * Set to true if you want to use raw JSON rather than converting to POJOs.
     */
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

    /**
     * Return only the specified number of messages. Works for threaded=true and threaded=extended.
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOlderThan() {
        return olderThan;
    }

    /**
     * Returns messages older than the message ID specified as a numeric string.
     * This is useful for paginating messages. For example, if you're currently viewing 20 messages and the oldest is number 2912,
     * you could append "?olderThan=2912″ to your request to get the 20 messages prior to those you're seeing.
     */
    public void setOlderThan(int olderThan) {
        this.olderThan = olderThan;
    }

    public int getNewerThan() {
        return newerThan;
    }

    /**
     * Returns messages newer than the message ID specified as a numeric string. This should be used when polling for new messages.
     * If you're looking at messages, and the most recent message returned is 3516, you can make a request with the parameter "?newerThan=3516″
     * to ensure that you do not get duplicate copies of messages already on your page.
     */
    public void setNewerThan(int newerThan) {
        this.newerThan = newerThan;
    }

    public String getThreaded() {
        return threaded;
    }

    /**
     * threaded=true will only return the first message in each thread.
     * This parameter is intended for apps which display message threads collapsed.
     * threaded=extended will return the thread starter messages in order of most recently active as well as the
     * two most recent messages, as they are viewed in the default view on the Yammer web interface.
     */
    public void setThreaded(String threaded) {
        this.threaded = threaded;
    }

    public String getUserId() {
        return userId;
    }

    /**
     * The user id
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

}
