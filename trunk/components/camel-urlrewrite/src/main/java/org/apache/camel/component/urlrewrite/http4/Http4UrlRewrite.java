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
package org.apache.camel.component.urlrewrite.http4;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Producer;
import org.apache.camel.component.http4.HttpServletUrlRewrite;
import org.apache.camel.component.urlrewrite.UrlRewriteFilter;

/**
 * The camel-http4 component implementation of the {@link org.apache.camel.component.http.HttpServletUrlRewrite}.
 */
public class Http4UrlRewrite extends UrlRewriteFilter implements HttpServletUrlRewrite {

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
