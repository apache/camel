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
package org.apache.camel.component.soroushbot.utils;

import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Assert;
import org.junit.Test;

public class MultiQueueWithTopicThreadPoolTest {
    @Test
    public void singleThreadSuccessful() throws InterruptedException {
        LinkedBlockingQueue<Integer> finalResultsOrder = new LinkedBlockingQueue<>();
        int capacity = 10;
        Integer[] results = new Integer[capacity];
        for (int i = 0; i < capacity; i++) {
            results[i] = i;
        }
        MultiQueueWithTopicThreadPool pool = new MultiQueueWithTopicThreadPool(1, capacity, "test");
        for (int i = 0; i < capacity; i++) {
            int finalI = i;
            pool.execute(i % 2, () -> {
                try {
                    Thread.sleep((capacity - finalI) * 100);
                    finalResultsOrder.add(finalI);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        Thread.sleep(capacity * (capacity + 2) * 100 / 2); // wait enough time for all task to be done
        Assert.assertArrayEquals("order of thread that executed is not what is expected", results, finalResultsOrder.toArray());
    }

    @Test(expected = IllegalStateException.class)
    public void singleThreadPoolSizeExceeded() {
        int capacity = 10;
        MultiQueueWithTopicThreadPool pool = new MultiQueueWithTopicThreadPool(1, capacity, "test");
        for (int i = 0; i < capacity + 2; i++) {
            pool.execute(i % 3, () -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Test
    public void multiThreadEndOrderSuccessful() throws InterruptedException {
        LinkedBlockingQueue<Integer> finalResultsOrder = new LinkedBlockingQueue<>();
        int totalJobs = 9;
        Integer[] results = new Integer[totalJobs];
        for (int i = 0; i < totalJobs / 3; i++) {
            /**
              0 1 2 3 4 5 6 7 8
              0 3 6 1 4 7 2 5 8 <- start order should be this
             */
            results[i] = i * 3;
            results[i + 3] = 3 * i + 1;
            results[i + 6] = 3 * i + 2;
        }
        MultiQueueWithTopicThreadPool pool = new MultiQueueWithTopicThreadPool(3, totalJobs, "test");
        for (int i = 0; i < totalJobs; i++) {
            int finalI = i;
            pool.execute(i % 3, () -> {
                try {
                    int mod3 = finalI % 3;
                    Thread.sleep((mod3 == 0 ? 1 : mod3 == 1 ? 4 : 13) * 10);
                    finalResultsOrder.add(finalI);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        Thread.sleep(130 * 3 + 500); // wait enough time for all task to be done
        Assert.assertArrayEquals("order of thread that executed is not what is expected", results, finalResultsOrder.toArray());
    }

    @Test
    public void multiThreadStartOrderSuccessful() throws InterruptedException {
        LinkedBlockingQueue<Integer> finalResultsOrder = new LinkedBlockingQueue<>();
        int totalJobs = 9;
        Integer[] expectedResults = new Integer[totalJobs];
        expectedResults[3] = 3;
        expectedResults[4] = 6;
        expectedResults[5] = 4;
        expectedResults[6] = 7;
        expectedResults[7] = 5;
        expectedResults[8] = 8;
        MultiQueueWithTopicThreadPool pool = new MultiQueueWithTopicThreadPool(3, totalJobs, "test");
        for (int i = 0; i < totalJobs; i++) {
            int finalI = i;
            pool.execute(i % 3, () -> {
                try {
                    int mod3 = finalI % 3;
                    finalResultsOrder.add(finalI);
                    Thread.sleep((mod3 == 0 ? 1 : mod3 == 1 ? 4 : 13) * 10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        Thread.sleep(130 * 3 + 100); // wait enough time for all task to be done
        Object[] finalResultsOrderList = finalResultsOrder.toArray();
        //order of first three job is not fully determined so we set them to null
        finalResultsOrderList[0] = finalResultsOrderList[1] = finalResultsOrderList[2] = null;
        Assert.assertArrayEquals("order of thread that executed is not what is expected", expectedResults, finalResultsOrderList);
    }

    @Test(expected = IllegalStateException.class)
    public void multiThreadPoolSizeExceeded() throws InterruptedException {
        LinkedBlockingQueue<Integer> finalResultsOrder = new LinkedBlockingQueue<>();
        int capacity = 3;
        int poolSize = 3;
        MultiQueueWithTopicThreadPool pool = new MultiQueueWithTopicThreadPool(poolSize, capacity, "test");
        for (int i = 0; i < (capacity + 1) * poolSize + 1; i++) {
            pool.execute(i % poolSize, () -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        Thread.sleep(100);
    }
}
