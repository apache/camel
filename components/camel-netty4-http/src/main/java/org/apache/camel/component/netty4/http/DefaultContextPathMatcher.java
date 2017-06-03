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
package org.apache.camel.component.netty4.http;

import java.util.Locale;

/**
 * A default {@link ContextPathMatcher} which supports the <tt>matchOnUriPrefix</tt> option.
 */
public class DefaultContextPathMatcher implements ContextPathMatcher {

    protected final String path;
    protected final boolean matchOnUriPrefix;

    public DefaultContextPathMatcher(String path, boolean matchOnUriPrefix) {
        this.path = path.toLowerCase(Locale.US);
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    @Override
    public boolean matches(String path) {
        path = path.toLowerCase(Locale.US);
        if (!matchOnUriPrefix) {
            // exact match
            return path.equals(this.path);
        } else {
            // match on prefix, then we just need to match the start of the context-path
            return path.startsWith(this.path);
        }
    }

    @Override
    public boolean matchesRest(String path, boolean wildcard) {
        return false;
    }

    @Override
    public boolean matchMethod(String method, String restrict) {
        // always match as HttpServerChannelHandler will deal with HTTP method restrictions
        return true;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultContextPathMatcher that = (DefaultContextPathMatcher) o;

        if (matchOnUriPrefix != that.matchOnUriPrefix) {
            return false;
        }
        if (!path.equals(that.path)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + (matchOnUriPrefix ? 1 : 0);
        return result;
    }
}
