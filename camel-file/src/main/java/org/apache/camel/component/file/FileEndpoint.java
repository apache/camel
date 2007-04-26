/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * @version $Revision: 523016 $
 */
public class FileEndpoint extends DefaultEndpoint<FileExchange> {
    private File file;
    protected FileEndpoint(File file,String endpointUri, Component component){
        super(endpointUri,component);
        this.file = file;
    }

   
    private ScheduledExecutorService executor;

    /**
     * @param file
     * @return a Consumer
     * @throws Exception
     * @see org.apache.camel.Endpoint#createConsumer(org.apache.camel.Processor)
     */
    public Consumer<FileExchange> createConsumer(Processor<FileExchange> file) throws Exception{
        return new FileConsumer(this, file, executor);
    }

    /**
     * @param file 
     * @return a FileExchange
     * @see org.apache.camel.Endpoint#createExchange()
     */
    public FileExchange createExchange(File file){
        return new FileExchange(getContext(),file);
    }
    
    /**
     * @return an Exchange
     * @see org.apache.camel.Endpoint#createExchange()
     */
    public FileExchange createExchange(){
        return createExchange(this.file);
    }


    /**
     * @return a Producer
     * @throws Exception
     * @see org.apache.camel.Endpoint#createProducer()
     */
    public Producer<FileExchange> createProducer() throws Exception{
        return new FileProducer(this);
    }

    
    /**
     * @return the executor
     */
    public synchronized ScheduledExecutorService getExecutor(){
        if (this.executor==null) {
            this.executor=new ScheduledThreadPoolExecutor(10);
        }
        return executor;
    }

    
    /**
     * @param executor the executor to set
     */
    public synchronized void setExecutor(ScheduledExecutorService executor){
        this.executor=executor;
    }
    
    public File getFile() {
        return file;
    }

	public boolean isSingleton() {
		return true;
	}
  
}
