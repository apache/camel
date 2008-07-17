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
package org.apache.camel.processor.resequencer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A thread that takes re-ordered {@link Exchange}s from a blocking queue and
 * send them to the linked processor.  
 * 
 * @author Martin Krasser
 * 
 * @version $Revision$
 */
public class SequenceSender extends Thread {

    private static final transient Log LOG = LogFactory.getLog(SequenceSender.class);
    private static final Exchange STOP = createStopSignal();
    
    private BlockingQueue<Exchange> queue;
    private Processor processor;
    
    /**
     * Creates a new {@link SequenceSender} thread.
     * 
     * @param processor the processor to send re-ordered {@link Exchange}s.
     */
    public SequenceSender(Processor processor) {
        this.processor = processor;
    }
    
    /**
     * Sets the {@link BlockingQueue} to take messages from.
     * 
     * @param queue the {@link BlockingQueue} to take messages from.
     */
    public void setQueue(BlockingQueue<Exchange> queue) {
        this.queue = queue;
    }

    public void run() {
        while (true) {
            try {
                Exchange exchange = queue.take();
                if (exchange == STOP) {
                    LOG.info("Exit processing loop after cancellation");
                    return;
                }
                processor.process(exchange);
            } catch (InterruptedException e) {
                LOG.info("Exit processing loop after interrupt");
                return;
            } catch (Exception e) {
                LOG.warn("Exception during exchange processing: " + e.getMessage());
            }
        }
    }
    
    /**
     * Cancels this thread.
     */
    public void cancel() throws InterruptedException {
        queue.put(STOP);
    }
    
    private static Exchange createStopSignal() {
        return (Exchange)Proxy.newProxyInstance(SequenceSender.class.getClassLoader(), 
                new Class[] {Exchange.class}, createStopHandler());
    }
    
    private static InvocationHandler createStopHandler() {
        return new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                throw new RuntimeException("Illegal method invocation on stop signal");
            }
        };
    }
    
}
