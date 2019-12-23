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
package org.apache.camel.example.cxf.httptojms;

import java.io.File;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;

public final class JmsBroker {
    JMSEmbeddedBroker jmsBrokerThread;
    String jmsBrokerUrl = "vm://localhost";
    String activeMQStorageDir;

    public JmsBroker() {
    }

    public JmsBroker(String url) {
        jmsBrokerUrl = url;
    }
    
    public void start() throws Exception {
        jmsBrokerThread = new JMSEmbeddedBroker(jmsBrokerUrl);
        jmsBrokerThread.startBroker();
    }
    
    public void stop() throws Exception {
        synchronized (this) {
            jmsBrokerThread.shutdownBroker = true;
        }
        if (jmsBrokerThread != null) {
            jmsBrokerThread.join();
        }
        
        jmsBrokerThread = null;
    }
    
    class JMSEmbeddedBroker extends Thread {
        boolean shutdownBroker;
        final String brokerUrl;
        Exception exception;

        JMSEmbeddedBroker(String url) {
            brokerUrl = url;
        }
        
        public void startBroker() throws Exception {
            synchronized (this) {
                super.start();
                try {
                    wait();
                    if (exception != null) {
                        throw exception;
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
        
        @Override
        public void run() {
            try {  
                BrokerService broker = new BrokerService();
                synchronized (this) {                                     
                    broker.setPersistenceAdapter(new MemoryPersistenceAdapter());                    
                    broker.setTmpDataDirectory(new File("./target"));
                    broker.addConnector(brokerUrl);
                    broker.start();
                    Thread.sleep(200);
                    notifyAll();
                }
                synchronized (this) {
                    while (!shutdownBroker) {
                        wait(1000);
                    }
                }                
                broker.stop();              
                broker = null;                
            } catch (Exception e) {
                exception = e;
                e.printStackTrace();
            }
        }
    }

}

