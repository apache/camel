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
package org.apache.camel.component.ignite;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.apache.ignite.IgniteException;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTask;
import org.apache.ignite.compute.ComputeTaskSplitAdapter;
import org.apache.ignite.events.Event;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteClosure;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.lang.IgniteReducer;
import org.apache.ignite.lang.IgniteRunnable;

public final class TestIgniteComputeResources {

    public static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static final IgniteRunnable TEST_RUNNABLE = new IgniteRunnable() {
        private static final long serialVersionUID = -4961602602993218883L;

        @Override
        public void run() {
            System.out.println("Hello from a runnable");
        }
    };

    public static final IgniteRunnable TEST_RUNNABLE_COUNTER = new IgniteRunnable() {
        private static final long serialVersionUID = 386219709871673366L;

        @Override
        public void run() {
            COUNTER.incrementAndGet();
        }
    };

    public static final IgnitePredicate<Event> EVENT_COUNTER = new IgnitePredicate<Event>() {
        private static final long serialVersionUID = -4214894278107593791L;

        @Override
        public boolean apply(Event event) {
            COUNTER.incrementAndGet();
            return true;
        }
    };

    public static final IgniteCallable<String> TEST_CALLABLE = new IgniteCallable<String>() {
        private static final long serialVersionUID = 986972344531961815L;

        @Override
        public String call() throws Exception {
            return "hello";
        }
    };

    public static final IgniteClosure<String, String> TEST_CLOSURE = new IgniteClosure<String, String>() {
        private static final long serialVersionUID = -3969758431961263815L;

        @Override
        public String apply(String input) {
            return "hello " + input;
        }
    };

    public static final ComputeTask<Integer, String> COMPUTE_TASK = new ComputeTaskSplitAdapter<Integer, String>() {
        private static final long serialVersionUID = 3040624379256407732L;

        @Override
        public String reduce(List<ComputeJobResult> results) throws IgniteException {
            StringBuilder answer = new StringBuilder();
            for (ComputeJobResult res : results) {
                Object data = res.getData();
                answer.append(data).append(",");
            }
            answer.deleteCharAt(answer.length() - 1);
            return answer.toString();
        }

        @Override
        protected Collection<? extends ComputeJob> split(int gridSize, final Integer arg) throws IgniteException {
            Set<ComputeJob> answer = new HashSet<>();
            for (int i = 0; i < arg; i++) {
                final int c = i;
                answer.add(new ComputeJob() {
                    private static final long serialVersionUID = 3365213549618276779L;

                    @Override
                    public Object execute() throws IgniteException {
                        return "a" + c;
                    }

                    @Override
                    public void cancel() {
                        // nothing
                    }
                });
            }
            return answer;
        }
    };

    public static final IgniteReducer<String, String> STRING_JOIN_REDUCER = new IgniteReducer<String, String>() {
        private static final long serialVersionUID = 1L;
        private List<String> list = Lists.newArrayList();

        @Override
        public boolean collect(String value) {
            list.add(value);
            return true;
        }

        @Override
        public String reduce() {
            Collections.sort(list);
            String answer = Joiner.on("").join(list);
            list.clear();
            return answer;
        }
    };
    
    private TestIgniteComputeResources() {
        
    }
}
