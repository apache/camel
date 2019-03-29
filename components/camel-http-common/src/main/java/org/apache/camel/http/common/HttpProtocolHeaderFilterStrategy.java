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
package org.apache.camel.http.common;

import org.apache.camel.support.DefaultHeaderFilterStrategy;

public class HttpProtocolHeaderFilterStrategy extends DefaultHeaderFilterStrategy {
    
    public HttpProtocolHeaderFilterStrategy() {
        initialize();  
    }

    // Just add the http headers here 
    protected void initialize() {
        getInFilter().add("host");
        
        getInFilter().add("content-encoding");
        getInFilter().add("content-language");
        getInFilter().add("content-location");
        getInFilter().add("content-md5");
        getInFilter().add("content-length");
        getInFilter().add("content-type");
        getInFilter().add("content-range");
        
        getInFilter().add("dav");
        getInFilter().add("depth");
        getInFilter().add("destination");
        getInFilter().add("etag");
        getInFilter().add("expect");
        getInFilter().add("expires");
        getInFilter().add("from");
        getInFilter().add("if");
        getInFilter().add("if-match");
        getInFilter().add("if-modified-since");
        getInFilter().add("if-none-match");
        getInFilter().add("if-range");
        getInFilter().add("if-unmodified-since");
        getInFilter().add("last-modified");
        getInFilter().add("location");
        getInFilter().add("lock-token");
        getInFilter().add("max-forwards");
        getInFilter().add("overwrite");
        getInFilter().add("pragma");
        getInFilter().add("proxy-authenticate");
        getInFilter().add("proxy-authorization");
        getInFilter().add("range");
        getInFilter().add("referer");
        getInFilter().add("retry-after");
        getInFilter().add("server");
        getInFilter().add("status-uri");
        getInFilter().add("te");
        getInFilter().add("timeout");
      
        getInFilter().add("user-agent");
        getInFilter().add("vary");
  
        getInFilter().add("www-authenticate");
        
        // Add the filter for the Generic Message header
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.5
        getInFilter().add("cache-control");
        getInFilter().add("connection");
        getInFilter().add("date");
        getInFilter().add("pragma");
        getInFilter().add("trailer");
        getInFilter().add("transfer-encoding");
        getInFilter().add("upgrade");
        getInFilter().add("via");
        getInFilter().add("warning");
               
        setLowerCase(true);
    }
}
