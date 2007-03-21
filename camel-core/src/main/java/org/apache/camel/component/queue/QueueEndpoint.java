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

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;

/**
 * Represents a queue endpoint that uses a {@link BlockingQueue}
 * object to process inbound exchanges.
 *
 * @org.apache.xbean.XBean
 * @version $Revision: 519973 $
 */
public class QueueEndpoint<E extends Exchange> extends DefaultEndpoint<E> {
    private BlockingQueue<E> queue;
	private org.apache.camel.component.queue.QueueEndpoint.Activation activation;

    public QueueEndpoint(String uri, CamelContext container, BlockingQueue<E> queue) {
        super(uri, container);
        this.queue = queue;
    }

    public void onExchange(E exchange) {
        queue.add(exchange);
    }

    public void setInboundProcessor(Processor<E> processor) {
        // TODO lets start a thread to process inbound requests
        // if we don't already have one
    }

    public E createExchange() {
    	// How can we create a specific Exchange if we are generic??
    	// perhaps it would be better if we did not implement this. 
        return (E) new DefaultExchange(getContext());
    }

    public Queue<E> getQueue() {
        return queue;
    }
    
    class Activation implements Runnable {
		AtomicBoolean stop = new AtomicBoolean();
		private Thread thread;
		
		public void run() {
			while(!stop.get()) {
				E exchange=null;
				try {
					exchange = queue.poll(100, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					break;
				}
				if( exchange !=null ) {
					try {
						getInboundProcessor().onExchange(exchange);
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			}
		}

		public void start() {
			thread = new Thread(this, getEndpointUri());
			thread.setDaemon(true);
			thread.start();
		}

		public void stop() throws InterruptedException {
			stop.set(true);
			thread.join();
		}
		
		@Override
		public String toString() {
			return "Activation: "+getEndpointUri();
		}
    }

    @Override
    protected void doActivate() {
		activation = new Activation();
		activation.start();
    }
    
    @Override
    protected void doDeactivate() {
		try {
			activation.stop();
			activation=null;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
    }
}
