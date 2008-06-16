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
package org.apache.camel.component.seda;

import java.util.concurrent.BlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An implementation of the <a href="http://activemq.apache.org/camel/queue.html">Queue components</a>
 * for asynchronous SEDA exchanges on a {@link BlockingQueue} within a CamelContext
 *
 * @deprecated This component has been deprecated; please use the seda: URI format instead of queue: 
 * @version $Revision$
 */
public class QueueComponent extends SedaComponent {
    private static final transient Log LOG = LogFactory.getLog(QueueComponent.class);

    public QueueComponent() {
        LOG.warn("This component has been deprecated; please use the seda: URI format instead of queue:");
    }
}
