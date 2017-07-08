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

public class ResequencerRunner<E> extends Thread {

    private ResequencerEngineSync<E> resequencer;

    private long interval;

    private boolean cancelRequested;

    private volatile boolean running;

    public ResequencerRunner(ResequencerEngineSync<E> resequencer, long interval) {
        this.resequencer = resequencer;
        this.interval = interval;
        this.cancelRequested = false;
    }

    @Override
    public void run() {
        while (!cancelRequested()) {
            running = true;
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                resequencer.deliver();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.run();
        running = false;
    }

    public synchronized void cancel() {
        this.cancelRequested = true;
    }

    private synchronized boolean cancelRequested() {
        return cancelRequested;
    }

    public boolean isRunning() {
        return running;
    }
}
