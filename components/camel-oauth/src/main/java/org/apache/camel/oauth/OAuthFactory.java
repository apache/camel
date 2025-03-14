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
package org.apache.camel.oauth;

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.oauth.jakarta.ServletOAuthFactory;
import org.apache.camel.oauth.vertx.VertxOAuthFactory;
import org.apache.camel.support.CamelContextHelper;

public abstract class OAuthFactory {

    protected final CamelContext context;

    public OAuthFactory(CamelContext context) {
        this.context = context;
    }

    public static OAuthFactory lookupFactory(CamelContext ctx) {
        var registry = ctx.getRegistry();
        var factory = registry.lookupByNameAndType(OAuthFactory.class.getName(), OAuthFactory.class);
        if (factory == null) {
            var vertxRouter = CamelContextHelper.lookup(ctx, "platform-http-router", VertxPlatformHttpRouter.class);
            if (vertxRouter != null) {
                factory = new VertxOAuthFactory(ctx);
                registry.bind(OAuthFactory.class.getName(), factory);
            } else {
                factory = new ServletOAuthFactory(ctx);
                registry.bind(OAuthFactory.class.getName(), factory);
            }
        }
        return factory;
    }

    public abstract OAuth createOAuth();

    public Optional<OAuth> findOAuth() {
        var registry = context.getRegistry();
        var oauth = registry.lookupByNameAndType(OAuth.class.getName(), OAuth.class);
        return Optional.ofNullable(oauth);
    }

}
