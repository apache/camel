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
package org.apache.camel.main;

import org.apache.camel.CamelContext;

/**
 * A useful base class for {@link org.apache.camel.main.MainListener} implementations.
 */
public class MainListenerSupport implements MainListener {

    @Override
    public void beforeInitialize(BaseMainSupport main) {
        // noop
    }

    @Override
    public void beforeConfigure(BaseMainSupport main) {
        // noop
    }

    @Override
    public void afterConfigure(BaseMainSupport main) {
        // noop
    }

    @Override
    @Deprecated
    public void configure(CamelContext context) {
        // noop
    }

    @Override
    public void beforeStart(BaseMainSupport main) {
        // noop
    }

    @Override
    public void afterStart(BaseMainSupport main) {
        // noop
    }

    @Override
    public void beforeStop(BaseMainSupport main) {
        // noop
    }

    @Override
    public void afterStop(BaseMainSupport main) {
        // noop
    }
}
