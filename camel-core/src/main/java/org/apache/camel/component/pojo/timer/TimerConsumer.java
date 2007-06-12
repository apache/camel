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
package org.apache.camel.component.pojo.timer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.camel.Processor;
import org.apache.camel.component.pojo.PojoExchange;
import org.apache.camel.component.pojo.PojoInvocation;
import org.apache.camel.impl.DefaultConsumer;

/**
 * @version $Revision: 523047 $
 */
public class TimerConsumer extends DefaultConsumer<PojoExchange> implements InvocationHandler {

    private final TimerEndpoint endpoint;
    private Timer timer;
    

	public TimerConsumer(TimerEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
		this.endpoint = endpoint;
    }
    
    @Override
    protected void doStart() throws Exception {
    	TimerComponent component = endpoint.getComponent();
    	component.addConsumer(this);    	
        timer=createTimerAndTask();
    }

    @Override
    protected void doStop() throws Exception {
        if(timer!=null){
            timer.cancel();
        }
    	TimerComponent component = endpoint.getComponent();
    	component.removeConsumer(this);    	
    }
    
    private Timer createTimerAndTask(){
    	
    	final Runnable proxy = createProxy();
        TimerTask task=new TimerTask(){
            @Override public void run(){
            	proxy.run();
            }
        };
        
        Timer result=new Timer(endpoint.getTimerName(),endpoint.isDaemon());
        if(endpoint.isFixedRate()){
            if(endpoint.getTime()!=null){
                result.scheduleAtFixedRate(task,endpoint.getTime(),endpoint.getPeriod());
            }else{
                result.scheduleAtFixedRate(task,endpoint.getDelay(),endpoint.getPeriod());
            }
        }else{
            if(endpoint.getTime()!=null){
                if(endpoint.getPeriod()>=0){
                    result.schedule(task,endpoint.getTime(),endpoint.getPeriod());
                }else{
                    result.schedule(task,endpoint.getTime());
                }
            }else{
                if(endpoint.getPeriod()>=0){
                    result.schedule(task,endpoint.getDelay(),endpoint.getPeriod());
                }else{
                    result.schedule(task,endpoint.getDelay());
                }
            }
        }
        return result;
    }
    
    /**
     * Creates a Proxy which generates the inbound PojoExchanges
     */
    public Runnable createProxy() {
        return (Runnable) Proxy.newProxyInstance(Runnable.class.getClassLoader(), new Class[]{Runnable.class}, this);
    }
    
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!isStarted()) {
            throw new IllegalStateException("The endpoint is not active: " + getEndpoint().getEndpointUri());
        }
        PojoInvocation invocation = new PojoInvocation(proxy, method, args);
        PojoExchange exchange = getEndpoint().createExchange();
        exchange.setInvocation(invocation);
        getProcessor().process(exchange);
        Throwable fault = exchange.getException();
        if (fault != null) {
            throw new InvocationTargetException(fault);
        }
        return exchange.getOut().getBody();
	}
  


}
