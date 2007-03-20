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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Component;
import org.apache.camel.Processor;

/**
 * Represents the component that manages {@link QueueEndpoint}.  It holds the 
 * list of named queues that queue endpoints reference.
 *
 * @org.apache.xbean.XBean
 * @version $Revision: 519973 $
 */
public class QueueComponent<E> implements Component<E, QueueEndpoint<E>> {
	
    private HashMap<String, Queue<E>> registry = new HashMap<String, Queue<E>>();
    private HashMap<QueueEndpoint<E>, Activation> activations = new HashMap<QueueEndpoint<E>, Activation>();
    
    class Activation implements Runnable {
		private final QueueEndpoint<E> endpoint;
		AtomicBoolean stop = new AtomicBoolean();
		private Thread thread;
		
		public Activation(QueueEndpoint<E> endpoint) {
			this.endpoint = endpoint;
		}

		public void run() {
			while(!stop.get()) {
				
			}
		}

		public void start() {
			thread = new Thread(this, endpoint.getEndpointUri());
			thread.setDaemon(true);
			thread.start();
		}

		public void stop() throws InterruptedException {
			stop.set(true);
			thread.join();
		}
    }

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

	public void activate(QueueEndpoint<E> endpoint, Processor<E> processor) {
		Activation activation = activations.get(endpoint);
		if( activation!=null ) {
			throw new IllegalArgumentException("Endpoint "+endpoint.getEndpointUri()+" has already been activated.");
		}
		
		activation = new Activation(endpoint);
		activation.start();
	}

	public void deactivate(QueueEndpoint<E> endpoint) {
		Activation activation = activations.remove(endpoint);
		if( activation==null ) {
			throw new IllegalArgumentException("Endpoint "+endpoint.getEndpointUri()+" is not activate.");
		}		
		try {
			activation.stop();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}
