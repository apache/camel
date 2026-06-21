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
package org.apache.camel.spi;

/**
 * SPI strategy for reloading {@link Resource}s in an already running {@link org.apache.camel.CamelContext}.
 * <p/>
 * The strategy watches resources (typically route files) and, on change, notifies its configured {@link ResourceReload}
 * listener so Camel can apply the updated definitions live. It is the resource-oriented counterpart to
 * {@link ContextReloadStrategy} and builds on the generic {@link ReloadStrategy}.
 * <p/>
 * See <a href="https://camel.apache.org/manual/route-reload.html">Route Reload</a> in the Camel user manual.
 *
 * @see   ContextReloadStrategy
 * @see   ResourceReload
 * @see   Resource
 * @since 3.14
 */
public interface ResourceReloadStrategy extends ReloadStrategy {

    /**
     * Gets the resource listener that is triggered on reload.
     */
    ResourceReload getResourceReload();

    /**
     * Sets the resource listener to trigger on reload.
     */
    void setResourceReload(ResourceReload listener);

}
