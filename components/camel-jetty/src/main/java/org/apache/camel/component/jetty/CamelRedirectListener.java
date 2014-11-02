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
package org.apache.camel.component.jetty;

import java.io.IOException;

import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.RedirectListener;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Buffer;

public class CamelRedirectListener extends RedirectListener {
    private final HttpExchange exchange;
    
    public CamelRedirectListener(HttpDestination destination, HttpExchange ex) {
        super(destination, ex);
        exchange = ex;
    }

    @Override
    public void onResponseStatus(Buffer version, int status, Buffer reason) throws IOException {
        // Update the exchange method to get to support the Post/Redirect/Get
        // http://en.wikipedia.org/wiki/Post/Redirect/Get
        if (exchange.getMethod().equals("POST") && (status == HttpStatus.SEE_OTHER_303 || status == HttpStatus.MOVED_TEMPORARILY_302)) {
            exchange.setMethod("GET");
        }
        
        // Since the default RedirectListener only cares about http
        // response codes 301 and 302, we override this method and
        // trick the super class into handling this case for us.
        if (status == HttpStatus.SEE_OTHER_303) {
            status = HttpStatus.MOVED_TEMPORARILY_302;
        }

        super.onResponseStatus(version, status, reason);
    }
}
