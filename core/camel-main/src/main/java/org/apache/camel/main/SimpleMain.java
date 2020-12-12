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
 * A minimal Main class for booting Camel.
 */
public class SimpleMain extends BaseMainSupport {
    public SimpleMain(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        postProcessCamelContext(camelContext);
    }

    @Override
    protected void doStart() throws Exception {
        for (MainListener listener : listeners) {
            listener.beforeStart(this);
        }

        super.doStart();

        getCamelContext().start();

        for (MainListener listener : listeners) {
            listener.afterStart(this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (MainListener listener : listeners) {
            listener.beforeStop(this);
        }

        super.doStop();

        getCamelContext().stop();

        for (MainListener listener : listeners) {
            listener.afterStop(this);
        }
    }
}
