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
package org.apache.camel.component.beanstalk;

public final class Headers {

    public static final String PREFIX = "beanstalk.";

    // in
    public static final String PRIORITY = PREFIX + "priority";
    public static final String DELAY = PREFIX + "delay";
    public static final String TIME_TO_RUN = PREFIX + "timeToRun";

    // in/out
    public static final String JOB_ID = PREFIX + "jobId";

    // out
    public static final String RESULT = PREFIX + "result";

    // other info
    public static final String TUBE = PREFIX + "tube";
    public static final String STATE = PREFIX + "state";
    public static final String AGE = PREFIX + "age";
    public static final String TIME_LEFT = PREFIX + "time-left";
    public static final String TIMEOUTS = PREFIX + "timeouts";
    public static final String RELEASES = PREFIX + "releases";
    public static final String BURIES = PREFIX + "buries";
    public static final String KICKS = PREFIX + "kicks";

    private Headers() {
    }

}