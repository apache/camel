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
package org.apache.camel.component.pojo;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Schedule used for polling an endpoint
 *
 */
public class PollingSchedule{

    private String name;
    private Timer timer;
    private Date time;
    private long period=-1;
    private long delay=-1;
    private boolean fixedRate;
    private boolean daemon=true;

    public PollingSchedule(String name,Date time){
        this(name,time,-1,-1,false);
    }

    public PollingSchedule(String name,Date time,long period){
        this(name,time,-1,period,false);
    }

    public PollingSchedule(String name,long delay){
        this(name,null,delay,-1,false);
    }
    
    public PollingSchedule(String name,long delay,long period){
        this(name,null,delay,period,false);
    }
    
    public PollingSchedule(){
    }

    PollingSchedule(String name,Date time,long delay,long period,boolean fixedRate){
        this.name=name;
        this.time=time;
        this.delay=delay;
        this.period=period;
        this.fixedRate=fixedRate;
    }

    void activate(Runnable run){
        timer=createTimerAndTask(name,run);
    }

    void deactivate(){
        if(timer!=null){
            timer.cancel();
        }
    }

    private Timer createTimerAndTask(String name,final Runnable run){
        TimerTask task=new TimerTask(){

            @Override public void run(){
                run.run();
            }
        };
        Timer result=new Timer(name,daemon);
        if(fixedRate){
            if(time!=null){
                result.scheduleAtFixedRate(task,time,period);
            }else{
                result.scheduleAtFixedRate(task,delay,period);
            }
        }else{
            if(time!=null){
                if(period>=0){
                    result.schedule(task,time,period);
                }else{
                    result.schedule(task,time);
                }
            }else{
                if(period>=0){
                    result.schedule(task,delay,period);
                }else{
                    result.schedule(task,delay);
                }
            }
        }
        return result;
    }

    
    /**
     * @return the daemon
     */
    public boolean isDaemon(){
        return this.daemon;
    }

    
    /**
     * @param daemon the daemon to set
     */
    public void setDaemon(boolean daemon){
        this.daemon=daemon;
    }

    
    /**
     * @return the delay
     */
    public long getDelay(){
        return this.delay;
    }

    
    /**
     * @param delay the delay to set
     */
    public void setDelay(long delay){
        this.delay=delay;
    }

    
    /**
     * @return the fixedRate
     */
    public boolean isFixedRate(){
        return this.fixedRate;
    }

    
    /**
     * @param fixedRate the fixedRate to set
     */
    public void setFixedRate(boolean fixedRate){
        this.fixedRate=fixedRate;
    }

    
    /**
     * @return the name
     */
    public String getName(){
        return this.name;
    }

    
    /**
     * @param name the name to set
     */
    public void setName(String name){
        this.name=name;
    }

    
    /**
     * @return the period
     */
    public long getPeriod(){
        return this.period;
    }

    
    /**
     * @param period the period to set
     */
    public void setPeriod(long period){
        this.period=period;
    }

    
    /**
     * @return the time
     */
    public Date getTime(){
        return this.time;
    }

    
    /**
     * @param time the time to set
     */
    public void setTime(Date time){
        this.time=time;
    }

    
    /**
     * @return the timer
     */
    public Timer getTimer(){
        return this.timer;
    }

    
    /**
     * @param timer the timer to set
     */
    public void setTimer(Timer timer){
        this.timer=timer;
    }
}
