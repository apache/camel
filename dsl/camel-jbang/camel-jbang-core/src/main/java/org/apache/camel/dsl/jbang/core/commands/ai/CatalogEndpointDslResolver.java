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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.java.in.EndpointDslResolver;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * The endpoint DSL with the catalog to hand (CAMEL-25148): a factory is an endpoint only when the catalog has the
 * component, so a helper method of the same shape is not taken for one, even in a snippet without imports; a
 * multi-value option gets the prefix the catalog gives it ({@code schedulerProperties("delay", 10)} is
 * {@code scheduler.delay=10}); an option the component does not have is reported.
 */
final class CatalogEndpointDslResolver implements EndpointDslResolver {

    private final CamelCatalog catalog;

    CatalogEndpointDslResolver(CamelCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Endpoint endpoint(String factory, List<String> args, List<Option> options) {
        Endpoint named = NAMING.endpoint(factory, args, List.of());
        if (named == null) {
            return null;
        }
        String scheme = named.uri().substring(0, named.uri().indexOf("://"));
        if (catalog.componentModel(scheme) == null) {
            // and(user, admin) is no endpoint
            return null;
        }
        // kafka("myKafka", "orders"): a component under another name, the catalog cannot say what its options are
        ComponentModel model = args.size() == 2 ? null : catalog.componentModel(scheme);
        List<Option> resolved = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Option o : options) {
            BaseOptionModel option = model != null ? option(model, o.name()) : null;
            if (model != null && option == null && !o.values().isEmpty()) {
                problems.add("no option " + o.name() + " on " + scheme);
                continue;
            }
            if (option != null && option.isMultiValue() && o.values().size() == 2 && option.getPrefix() != null) {
                resolved.add(new Option(option.getPrefix() + o.values().get(0), List.of(o.values().get(1))));
            } else {
                resolved.add(o);
            }
        }
        Endpoint answer = NAMING.endpoint(factory, args, resolved);
        if (answer == null) {
            return null;
        }
        problems.addAll(answer.problems());
        return new Endpoint(answer.uri(), problems);
    }

    private static BaseOptionModel option(ComponentModel model, String name) {
        for (BaseOptionModel o : model.getEndpointOptions()) {
            if (o.getName().equals(name)) {
                return o;
            }
        }
        return null;
    }
}
