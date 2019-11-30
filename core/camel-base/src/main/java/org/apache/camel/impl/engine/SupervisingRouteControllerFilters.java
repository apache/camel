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
package org.apache.camel.impl.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Route;

/**
 * A {@link SupervisingRouteController.Filter} which blacklists routes.
 */
public final class SupervisingRouteControllerFilters {

    private SupervisingRouteControllerFilters() {
    }

    public static final class BlackList implements SupervisingRouteController.Filter {
        private final Set<String> names;

        public BlackList(String name) {
            this(Collections.singletonList(name));
        }

        public BlackList(Collection<String> names) {
            this.names = new HashSet<>(names);
        }

        @Override
        public SupervisingRouteController.FilterResult apply(Route route) {
            boolean supervised = !names.contains(route.getId());

            return new SupervisingRouteController.FilterResult(
                supervised,
                supervised ? null : "black-listed"
            );
        }
    }
}
