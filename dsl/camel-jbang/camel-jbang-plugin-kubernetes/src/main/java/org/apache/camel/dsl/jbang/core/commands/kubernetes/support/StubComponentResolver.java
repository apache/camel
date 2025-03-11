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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.support;

import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.component.stub.StubComponent;
import org.apache.camel.impl.engine.DefaultComponentResolver;

public final class StubComponentResolver extends DefaultComponentResolver {
    private static final Set<String> ACCEPTED_STUB_NAMES = Set.of(
            "stub", "bean", "class", "direct", "kamelet", "log", "platform-http", "rest", "rest-api", "seda", "vertx-http");

    private final Set<String> names;
    private final String stubPattern;
    private final boolean silent;

    public StubComponentResolver(String stubPattern, boolean silent) {
        this.names = new TreeSet<>();
        this.stubPattern = stubPattern;
        this.silent = silent;
    }

    @Override
    public Component resolveComponent(String name, CamelContext context) {
        final boolean accept = accept(name);
        final Component answer = super.resolveComponent(accept ? name : "stub", context);

        if ((silent || stubPattern != null) && answer instanceof StubComponent sc) {
            // enable shadow mode on stub component
            sc.setShadow(true);
            sc.setShadowPattern(stubPattern);
        }

        this.names.add(name);

        return answer;
    }

    private boolean accept(String name) {
        if (stubPattern == null) {
            return true;
        }

        // we are stubbing but need to accept the following
        return ACCEPTED_STUB_NAMES.contains(name);
    }

    public Set<String> getNames() {
        return Set.copyOf(names);
    }
}
