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
package org.apache.camel.jmxconnect;

/**
 * This is based heavily on the DynamicService example from the mx4j project
 *
 * @version $Revision$
 */
public class SimpleService implements Runnable, SimpleServiceMBean {
    private boolean running;
    private int simpleCounter = 0;
    private int simpleValue = 0;

    public void start() {
        if (!running) {
            running = true;
            Thread thread = new Thread(this);
            thread.start();
        }
    }

    public void stop() {
        running = false;
    }

    public void run() {
        while (running) {
            simpleCounter++;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void setSimpleCounter(int simpleCounter) {
        this.simpleCounter = simpleCounter;
    }

    public int getSimpleCounter() {
        return simpleCounter;
    }

    public int getSimpleValue() {
        return simpleValue;
    }

    public void setSimpleValue(int simpleValue) {
        this.simpleValue = simpleValue;
    }
/*

    protected MBeanAttributeInfo[] createMBeanAttributeInfo(){
        return new MBeanAttributeInfo[] {
                new MBeanAttributeInfo("SimpleCounter","int","test simpleCounter",true,false,false),
                new MBeanAttributeInfo("SimpleValue","int","test simpleValue",true,true,false) };
    }

    protected MBeanOperationInfo[] createMBeanOperationInfo(){
        return new MBeanOperationInfo[] {
                new MBeanOperationInfo("start","Starts the SimpleService",new MBeanParameterInfo[0],"void",
                                MBeanOperationInfo.ACTION),
                new MBeanOperationInfo("stop","Stops the SimpleService",new MBeanParameterInfo[0],"void",
                                MBeanOperationInfo.ACTION) };
    }*/


}
