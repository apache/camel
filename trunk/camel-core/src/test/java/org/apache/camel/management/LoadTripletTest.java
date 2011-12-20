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
package org.apache.camel.management;

import junit.framework.TestCase;
import org.apache.camel.management.mbean.LoadTriplet;

public class LoadTripletTest extends TestCase {

    public void testConstantUpdate() {
        LoadTriplet t = new LoadTriplet();
        t.update(1);
        assertEquals(1.0, t.getLoad1(), Math.ulp(1.0) * 5);
        assertEquals(1.0, t.getLoad5(), Math.ulp(1.0) * 5);
        assertEquals(1.0, t.getLoad15(), Math.ulp(1.0) * 5);
        for (int i = 0; i < 100; i++) {
            t.update(1);
        }
        assertEquals(1.0, t.getLoad1(), Math.ulp(1.0) * 5);
        assertEquals(1.0, t.getLoad5(), Math.ulp(1.0) * 5);
        assertEquals(1.0, t.getLoad15(), Math.ulp(1.0) * 5);
    }

    public void testChargeDischarge() {
        LoadTriplet t = new LoadTriplet();
        t.update(0);
        double last = t.getLoad15();
        double lastDiff = Double.MAX_VALUE;
        double diff;
        for (int i = 0; i < 1000; i++) {
            t.update(5);
            diff = t.getLoad15() - last;
            assertTrue(diff > 0.0);
            assertTrue(diff < lastDiff);
            lastDiff = diff;
            last = t.getLoad15();
        }
        lastDiff = -Double.MAX_VALUE;
        for (int i = 0; i < 1000; i++) {
            t.update(0);
            diff = t.getLoad15() - last;
            assertTrue(diff < 0.0);
            assertTrue(String.format("%f is smaller than %f", diff, lastDiff), diff > lastDiff);
            lastDiff = diff;
            last = t.getLoad15();
        }
    }

}
