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
package org.apache.camel.cdi;

/* package-private */ final class CdiCamelConfigurationEvent implements CdiCamelConfiguration {

    private boolean autoConfigureRoutes = true;
    private boolean autoStartContexts = true;
    private volatile boolean unmodifiable;

    @Override
    public CdiCamelConfiguration autoConfigureRoutes(boolean autoConfigureRoutes) {
        throwsIfUnmodifiable();
        this.autoConfigureRoutes = autoConfigureRoutes;
        return this;
    }

    @Override
    public boolean autoConfigureRoutes() {
        return autoConfigureRoutes;
    }

    @Override
    public CdiCamelConfiguration autoStartContexts(boolean autoStartContexts) {
        throwsIfUnmodifiable();
        this.autoStartContexts = autoStartContexts;
        return this;
    }

    @Override
    public boolean autoStartContexts() {
        return autoStartContexts;
    }

    void unmodifiable() {
        unmodifiable = true;
    }

    private void throwsIfUnmodifiable() {
        if (unmodifiable) {
            throw new IllegalStateException(
                    "Camel CDI configuration event must not be used outside " + "its observer method!");
        }
    }
}