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
package org.apache.camel.spi;

import java.io.Closeable;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.CamelContextTrackerRegistry;

/**
 * A {@link CamelContext} creation tracker.
 */
public class CamelContextTracker implements Closeable {

    public interface Filter {

        boolean accept(CamelContext camelContext);

    }

    private final Filter filter;

    public CamelContextTracker() {
        filter = new Filter() {
            public boolean accept(CamelContext camelContext) {
                return !camelContext.getClass().getName().contains("Proxy");
            }
        };
    }

    public CamelContextTracker(Filter filter) {
        this.filter = filter;
    }

    /**
     * Called to determine whether this tracker should accept the given context.
     */
    public boolean accept(CamelContext camelContext) {
        return filter == null || filter.accept(camelContext);
    }

    /**
     * Called when a context is created.
     */
    public void contextCreated(CamelContext camelContext) {
        // do nothing
    }

    public final void open() {
        CamelContextTrackerRegistry.INSTANCE.addTracker(this);
    }

    public final void close() {
        CamelContextTrackerRegistry.INSTANCE.removeTracker(this);
    }
}