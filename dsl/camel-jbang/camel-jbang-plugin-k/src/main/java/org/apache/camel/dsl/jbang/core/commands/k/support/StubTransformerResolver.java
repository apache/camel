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

package org.apache.camel.dsl.jbang.core.commands.k.support;

import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultTransformerResolver;
import org.apache.camel.main.stub.StubTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;

public final class StubTransformerResolver extends DefaultTransformerResolver {
    private final Set<String> names;
    private final String stubPattern;
    private final boolean silent;

    public StubTransformerResolver(String stubPattern, boolean silent) {
        this.names = new TreeSet<>();
        this.stubPattern = stubPattern;
        this.silent = silent;
    }

    @Override
    public Transformer resolve(TransformerKey key, CamelContext context) {
        final boolean accept = accept(key.toString());
        final Transformer answer = accept ? super.resolve(key, context) : new StubTransformer();

        this.names.add(key.toString());

        return answer;
    }

    private boolean accept(String name) {
        if (stubPattern == null) {
            return true;
        }

        return false;
    }

    public Set<String> getNames() {
        return Set.copyOf(this.names);
    }
}
