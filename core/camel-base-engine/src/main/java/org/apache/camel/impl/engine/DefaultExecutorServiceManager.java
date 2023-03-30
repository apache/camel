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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * Default {@link org.apache.camel.spi.ExecutorServiceManager}.
 */
public class DefaultExecutorServiceManager extends BaseExecutorServiceManager {

    public DefaultExecutorServiceManager(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
        return super.newThreadPool(forceId(source), name, profile);
    }

    @Override
    public ExecutorService newCachedThreadPool(Object source, String name) {
        return super.newCachedThreadPool(forceId(source), name);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, ThreadPoolProfile profile) {
        return super.newScheduledThreadPool(forceId(source), name, profile);
    }

    protected Object forceId(Object source) {
        if (source instanceof NamedNode && source instanceof IdAware) {
            NamedNode node = (NamedNode) source;
            NodeIdFactory factory = getCamelContext().getCamelContextExtension().getContextPlugin(NodeIdFactory.class);
            if (node.getId() == null) {
                String id = factory.createId(node);
                // we auto generated an id to be assigned
                ((IdAware) source).setGeneratedId(id);
            }
        }
        return source;
    }

}
