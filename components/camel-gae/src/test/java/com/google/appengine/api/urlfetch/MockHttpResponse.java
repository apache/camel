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
package com.google.appengine.api.urlfetch;

import java.util.List;

@SuppressWarnings("serial")
public class MockHttpResponse extends HTTPResponse {

    public MockHttpResponse(int responseCode) {
        super(responseCode);
    }

    @Override
    public void addHeader(String name, String value) {
        super.addHeader(name, value);
    }

    @Override
    public byte[] getContent() {
        return super.getContent();
    }

    @Override
    public List<HTTPHeader> getHeaders() {
        return super.getHeaders();
    }

    public HTTPHeader getHeader(String name) {
        for (HTTPHeader header : getHeaders()) {
            if (header.getName().equalsIgnoreCase(name)) {
                return header;
            }
        }
        return null;
    }
    
    @Override
    public void setContent(byte[] content) {
        super.setContent(content);
    }

}
