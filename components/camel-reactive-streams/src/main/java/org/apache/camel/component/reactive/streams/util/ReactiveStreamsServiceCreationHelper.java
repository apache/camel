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
package org.apache.camel.component.reactive.streams.util;

import org.apache.camel.CamelContext;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.spi.FactoryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to create the reactive-streams service from factory finders.
 * Users should not use this class directly, as it may be removed in future versions.
 */
public final class ReactiveStreamsServiceCreationHelper {

    private static final Logger LOG = LoggerFactory.getLogger(CamelReactiveStreams.class);

    private ReactiveStreamsServiceCreationHelper() {
    }

    @SuppressWarnings("unchecked")
    public static CamelReactiveStreamsService createNewReactiveStreamsService(CamelContext context, String name) {
        if (name == null) {
            name = "default-service";
        }

        String path = "META-INF/services/org/apache/camel/reactive-streams/";
        Class<? extends CamelReactiveStreamsService> serviceClass;
        try {
            FactoryFinder finder = context.getFactoryFinder(path);
            LOG.trace("Using FactoryFinder: {}", finder);
            serviceClass = (Class<? extends CamelReactiveStreamsService>) finder.findClass(name);
            return serviceClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class referenced in '" + path + name + "' not found", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create the reactive stream service defined in '" + path + name + "'", e);
        }

    }

}
