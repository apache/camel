/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.graalvm;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.main.MainSupport;
import org.apache.camel.model.ModelCamelContext;

public abstract class CamelRuntime extends RouteBuilder {

    private Main main = new Main();
    private SimpleRegistry registry = new SimpleRegistry();
    private CamelContext context = new FastCamelContext(registry);

    public CamelRuntime() {
        setContext(context);
    }

    public void bind(String name, Object object) {
        registry.put(name, object);
    }

    public void run(String[] args) throws Exception {
        main.setRouteBuilders(Collections.singletonList(this));
        main.run(args);
    }

    @Override
    protected ModelCamelContext createContainer() {
        throw new IllegalStateException();
    }

    public SimpleRegistry getRegistry() {
        return registry;
    }

    class Main extends MainSupport {
        public Main() {
            options.removeIf(o -> "-r".equals(o.getAbbreviation()));
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();
            context.stop();
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();
            postProcessContext();
            context.start();
        }

        @Override
        protected ProducerTemplate findOrCreateCamelTemplate() {
            return context.createProducerTemplate();
        }

        @Override
        protected Map<String, CamelContext> getCamelContextMap() {
            return Collections.singletonMap("camel-1", context);
        }
    }
}
