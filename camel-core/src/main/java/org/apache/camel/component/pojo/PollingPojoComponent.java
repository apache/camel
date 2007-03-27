/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.apache.camel.component.pojo;

import java.util.Date;
import java.util.HashMap;

/**
 * Represents the polling component that manages {@link PojoEndpoint}. It holds the list of named pojos that queue
 * endpoints reference.
 * 
 * @version $Revision: 519973 $
 */
public class PollingPojoComponent extends PojoComponent{

    protected final HashMap<String,PollingSchedule> schedules=new HashMap<String,PollingSchedule>();

    /**
     * Registers a pojo and schedules the pojo for execution at the specified time. If the time is in the past, the task
     * is scheduled for immediate execution.
     * 
     * @param uri
     * @param pojo
     * @param schedule
     * @throws IllegalArgumentException if <tt>time.getTime()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or cancelled, timer was cancelled, or timer thread
     *             terminated.
     */
    public void registerAndSchedulePojo(String uri,Object pojo,PollingSchedule schedule){
        super.addService(uri,pojo);
        schedules.put(uri,schedule);
    }

    /**
     * Registers a pojo and schedules the pojo for execution at the specified time. If the time is in the past, the task
     * is scheduled for immediate execution.
     * 
     * @param uri
     * @param pojo
     * @param time time at which task is to be executed.
     * @throws IllegalArgumentException if <tt>time.getTime()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or cancelled, timer was cancelled, or timer thread
     *             terminated.
     */
    public void registerAndSchedulePojo(String uri,Object pojo,Date time){
        super.addService(uri,pojo);
        PollingSchedule schedule=new PollingSchedule(uri,time);
        schedules.put(uri,schedule);
    }

    /**
     * Registers a pojo and cchedules the pojo for execution at the specified time. If the time is in the past, the task
     * is scheduled for immediate execution.
     * 
     * @param uri
     * @param pojo
     * @param firstTime
     * @param period
     * @throws IllegalArgumentException if <tt>time.getTime()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or cancelled, timer was cancelled, or timer thread
     *             terminated.
     */
    public void registerAndScheulePojo(String uri,Object pojo,Date firstTime,long period){
        super.addService(uri,pojo);
        PollingSchedule schedule=new PollingSchedule(uri,firstTime,period);
        schedules.put(uri,schedule);
    }

    /**
     * Registers a pojo and schedules the specified task for repeated <i>fixed-delay execution</i>, beginning after the
     * specified delay. Subsequent executions take place at approximately regular intervals separated by the specified
     * period.
     * 
     * <p>
     * In fixed-delay execution, each execution is scheduled relative to the actual execution time of the previous
     * execution. If an execution is delayed for any reason (such as garbage collection or other background activity),
     * subsequent executions will be delayed as well. In the long run, the frequency of execution will generally be
     * slightly lower than the reciprocal of the specified period (assuming the system clock underlying
     * <tt>Object.wait(long)</tt> is accurate).
     * 
     * <p>
     * Fixed-delay execution is appropriate for recurring activities that require "smoothness." In other words, it is
     * appropriate for activities where it is more important to keep the frequency accurate in the short run than in the
     * long run. This includes most animation tasks, such as blinking a cursor at regular intervals. It also includes
     * tasks wherein regular activity is performed in response to human input, such as automatically repeating a
     * character as long as a key is held down.
     * 
     * @param uri
     * @param pojo
     * @param delay delay in milliseconds before task is to be executed.
     * @param period time in milliseconds between successive task executions.
     * @throws IllegalArgumentException if <tt>delay</tt> is negative, or <tt>delay + System.currentTimeMillis()</tt>
     *             is negative.
     * @throws IllegalStateException if task was already scheduled or cancelled, timer was cancelled, or timer thread
     *             terminated.
     */
    public void registerAndScheulePojo(String uri,Object pojo,long delay,long period){
        super.addService(uri,pojo);
        PollingSchedule schedule=new PollingSchedule(uri,delay,period);
        schedules.put(uri,schedule);
    }

    /**
     * Register pojo and schedules the specified task for repeated <i>fixed-rate execution</i>, beginning at the
     * specified time. Subsequent executions take place at approximately regular intervals, separated by the specified
     * period.
     * 
     * <p>
     * In fixed-rate execution, each execution is scheduled relative to the scheduled execution time of the initial
     * execution. If an execution is delayed for any reason (such as garbage collection or other background activity),
     * two or more executions will occur in rapid succession to "catch up." In the long run, the frequency of execution
     * will be exactly the reciprocal of the specified period (assuming the system clock underlying
     * <tt>Object.wait(long)</tt> is accurate).
     * 
     * <p>
     * Fixed-rate execution is appropriate for recurring activities that are sensitive to <i>absolute</i> time, such as
     * ringing a chime every hour on the hour, or running scheduled maintenance every day at a particular time. It is
     * also appropriate for recurring activities where the total time to perform a fixed number of executions is
     * important, such as a countdown timer that ticks once every second for ten seconds. Finally, fixed-rate execution
     * is appropriate for scheduling multiple repeating timer tasks that must remain synchronized with respect to one
     * another.
     * 
     * @param uri
     * @param pojo
     * @param firstTime First time at which task is to be executed.
     * @param period time in milliseconds between successive task executions.
     * @throws IllegalArgumentException if <tt>time.getTime()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or cancelled, timer was cancelled, or timer thread
     *             terminated.
     */
    public void registerAndScheulePojoAtFixedRate(String uri,Object pojo,Date firstTime,long period){
        super.addService(uri,pojo);
        PollingSchedule schedule=new PollingSchedule(uri,firstTime,-1,period,true);
        schedules.put(uri,schedule);
    }

    /**
     * Register pojo and schedules the specified task for repeated <i>fixed-rate execution</i>, beginning after the
     * specified delay. Subsequent executions take place at approximately regular intervals, separated by the specified
     * period.
     * 
     * <p>
     * In fixed-rate execution, each execution is scheduled relative to the scheduled execution time of the initial
     * execution. If an execution is delayed for any reason (such as garbage collection or other background activity),
     * two or more executions will occur in rapid succession to "catch up." In the long run, the frequency of execution
     * will be exactly the reciprocal of the specified period (assuming the system clock underlying
     * <tt>Object.wait(long)</tt> is accurate).
     * 
     * <p>
     * Fixed-rate execution is appropriate for recurring activities that are sensitive to <i>absolute</i> time, such as
     * ringing a chime every hour on the hour, or running scheduled maintenance every day at a particular time. It is
     * also appropriate for recurring activities where the total time to perform a fixed number of executions is
     * important, such as a countdown timer that ticks once every second for ten seconds. Finally, fixed-rate execution
     * is appropriate for scheduling multiple repeating timer tasks that must remain synchronized with respect to one
     * another.
     * 
     * @param uri
     * @param pojo
     * @param delay delay in milliseconds before task is to be executed.
     * @param period time in milliseconds between successive task executions.
     * @throws IllegalArgumentException if <tt>delay</tt> is negative, or <tt>delay + System.currentTimeMillis()</tt>
     *             is negative.
     * @throws IllegalStateException if task was already scheduled or cancelled, timer was cancelled, or timer thread
     *             terminated.
     */
    public void registerAndScheulePojoAtFixedRate(String uri,Object pojo,long delay,long period){
        super.addService(uri,pojo);
        PollingSchedule schedule=new PollingSchedule(uri,null,delay,period,true);
        schedules.put(uri,schedule);
    }

    public void registerActivation(String uri,PojoEndpoint endpoint){
//        super.registerActivation(uri,endpoint);
//        if(endpoint instanceof PollingPojoEndpoint){
        if(endpoint instanceof PojoEndpoint){
            PollingSchedule schedule=schedules.get(uri);
            if(schedule!=null){
                schedule.activate((Runnable)endpoint);
            }
        }
    }

    public void removeConsumer(String uri){
        super.removeConsumer(uri);
        PollingSchedule schedule=schedules.remove(uri);
        if(schedule!=null){
            schedule.deactivate();
        }
    }
}
