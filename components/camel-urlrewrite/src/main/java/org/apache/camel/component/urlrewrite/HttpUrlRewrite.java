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
package org.apache.camel.component.urlrewrite;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Producer;
import org.apache.camel.http.common.HttpServletUrlRewrite;

/**
 * The camel-http component implementation of the {@link HttpServletUrlRewrite}.
 */
public class HttpUrlRewrite extends UrlRewriteFilter implements HttpServletUrlRewrite {

    @Override
    public String rewrite(String url, String relativeUrl, Producer producer, HttpServletRequest request) throws Exception {
        return rewrite(relativeUrl, request);
    }

    @Override
    public String rewrite(String url, String relativeUrl, Producer producer) throws Exception {
        // not in use
        return null;
    }
}
