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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class SparkConfiguration implements Cloneable {

    @UriParam(defaultValue = "true")
    private boolean mapHeaders = true;
    @UriParam
    private boolean disableStreamCache;
    @UriParam
    private boolean urlDecodeHeaders;
    @UriParam
    private boolean transferException;
    @UriParam(label = "advanced")
    private boolean matchOnUriPrefix;

    public boolean isMapHeaders() {
        return mapHeaders;
    }

    /**
     * Returns a copy of this configuration
     */
    public SparkConfiguration copy() {
        try {
            SparkConfiguration copy = (SparkConfiguration) clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * If this option is enabled, then during binding from Spark to Camel Message then the headers will be mapped as well
     * (eg added as header to the Camel Message as well). You can turn off this option to disable this.
     * The headers can still be accessed from the org.apache.camel.component.sparkrest.SparkMessage message with the
     * method getRequest() that returns the Spark HTTP request instance.
     */
    public void setMapHeaders(boolean mapHeaders) {
        this.mapHeaders = mapHeaders;
    }

    public boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    /**
     * Determines whether or not the raw input stream from Spark HttpRequest#getContent() is cached or not
     * (Camel will read the stream into a in light-weight memory based Stream caching) cache.
     * By default Camel will cache the Netty input stream to support reading it multiple times to ensure Camel
     * can retrieve all data from the stream. However you can set this option to true when you for example need
     * to access the raw stream, such as streaming it directly to a file or other persistent store.
     * Mind that if you enable this option, then you cannot read the Netty stream multiple times out of the box,
     * and you would need manually to reset the reader index on the Spark raw stream.
     */
    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    public boolean isUrlDecodeHeaders() {
        return urlDecodeHeaders;
    }

    /**
     * If this option is enabled, then during binding from Spark to Camel Message then the header values will be URL decoded (eg %20 will be a space character.)
     */
    public void setUrlDecodeHeaders(boolean urlDecodeHeaders) {
        this.urlDecodeHeaders = urlDecodeHeaders;
    }

    public boolean isTransferException() {
        return transferException;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception was send back serialized
     * in the response as a application/x-java-serialized-object content type.
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    /**
     * Whether or not the consumer should try to find a target consumer by matching the URI prefix if no exact match is found.
     */
    public void setMatchOnUriPrefix(boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

}
