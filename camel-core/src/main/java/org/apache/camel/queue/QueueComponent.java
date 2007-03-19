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
package org.apache.camel.queue;

import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents the component that manages {@link QueueEndpoint}.  It holds the 
 * list of named queues that queue endpoints reference.
 *
 * @version $Revision: 519973 $
 */
public class QueueComponent<E> {
	
    private HashMap<String, Queue<E>> registry = new HashMap<String, Queue<E>>();

	synchronized public Queue<E> getOrCreateQueue(String uri) {
		Queue<E> queue = registry.get(uri);
		if( queue == null ) {
			queue = createQueue();
			registry.put(uri, queue);
		}
		return queue;
	}

	private Queue<E> createQueue() {
		return new LinkedBlockingQueue<E>();
	}
    
}
