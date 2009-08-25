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
package org.apache.camel.management;

import java.util.EventObject;

import org.apache.camel.spi.EventNotifier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default event notifier that only notifies if <tt>TRACE</tt> log level has
 * been configured for its logger.
 *
 * @version $Revision$
 */
public class DefaultEventNotifier implements EventNotifier {

    private static final Log LOG = LogFactory.getLog(DefaultEventNotifier.class);

    public void notify(EventObject event) throws Exception {
        LOG.trace("Event: " + event);
    }

    public boolean isEnabled(EventObject event) {
        return LOG.isTraceEnabled();
    }

}
