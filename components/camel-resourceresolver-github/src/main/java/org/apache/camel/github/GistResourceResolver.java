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
package org.apache.camel.github;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.ResourceResolver;
import org.apache.camel.support.service.ServiceSupport;

@ResourceResolver("gist")
public class GistResourceResolver extends ServiceSupport implements org.apache.camel.spi.ResourceResolver {

    // gist:davsclaus:477ddff5cdeb1ae03619aa544ce47e92
    // https://gist.githubusercontent.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92/raw
    private static final String GIST_URL = "https://gist.githubusercontent.com/%s/%s/raw";

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getSupportedScheme() {
        return "gist";
    }

    @Override
    public Resource resolve(String location) {
        String[] parts = location.split(":");
        // scheme not in use as its gist
        String user = null;
        String gid = null;

        if (parts.length == 3) {
            user = parts[1];
            gid = parts[2];
        }
        if (user == null || gid == null) {
            throw new IllegalArgumentException(location);
        }

        String target = String.format(GIST_URL, user, gid);
        return new GistResource(camelContext, target);
    }
}
