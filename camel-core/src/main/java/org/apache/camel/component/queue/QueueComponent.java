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
package org.apache.camel.component.queue;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents the component that manages {@link QueueEndpoint}.  It holds the 
 * list of named queues that queue endpoints reference.
 *
 * @org.apache.xbean.XBean
 * @version $Revision: 519973 $
 */
public class QueueComponent<E extends Exchange> extends DefaultComponent<E> {
	
	public BlockingQueue<E> createQueue() {
		return new LinkedBlockingQueue<E>();
	}

    @Override
    protected Endpoint<E> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        return new QueueEndpoint<E>(uri, this);
    }
}
