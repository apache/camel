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
package org.apache.camel.blueprint;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BlueprintCamelContext extends DefaultCamelContext {

    private static final transient Log LOG = LogFactory.getLog(BlueprintCamelContext.class);

    private boolean shouldStartContext = ObjectHelper.getSystemProperty("shouldStartContext", Boolean.TRUE);

    public void init() throws Exception {
        maybeStart();
    }

    private void maybeStart() throws Exception {
        if (!getShouldStartContext()) {
            LOG.info("Not starting Apache Camel as property ShouldStartContext is false");
            return;
        }

        if (!isStarted() && !isStarting()) {
            // Make sure we will not get into the endless loop of calling star
            LOG.info("Starting Apache Camel as property ShouldStartContext is true");
            start();
        } else {
            // ignore as Camel is already started
            LOG.trace("Ignoring maybeStart() as Apache Camel is already started");
        }
    }

    public void destroy() throws Exception {
        stop();
    }

    public void setShouldStartContext(boolean shouldStartContext) {
        this.shouldStartContext = shouldStartContext;
    }

    public boolean getShouldStartContext() {
        return shouldStartContext;
    }

}
