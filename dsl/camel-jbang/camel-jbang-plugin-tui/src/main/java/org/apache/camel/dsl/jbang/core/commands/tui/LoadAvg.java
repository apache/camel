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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.Locale;

class LoadAvg {
    private static final double EXP_1 = Math.exp(-1 / 60.0);
    private static final double EXP_5 = Math.exp(-1 / (60.0 * 5.0));
    private static final double EXP_15 = Math.exp(-1 / (60.0 * 15.0));

    private double load1 = Double.NaN;
    private double load5 = Double.NaN;
    private double load15 = Double.NaN;

    synchronized void update(double value) {
        load1 = Double.isNaN(load1) ? value : value + EXP_1 * (load1 - value);
        load5 = Double.isNaN(load5) ? value : value + EXP_5 * (load5 - value);
        load15 = Double.isNaN(load15) ? value : value + EXP_15 * (load15 - value);
    }

    synchronized String format(String fmt) {
        return Double.isNaN(load1) ? "-" : String.format(Locale.US, fmt, load1, load5, load15);
    }
}
