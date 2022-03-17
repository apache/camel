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

import org.apache.camel.spi.Metadata;

public final class Headers {

    public static final String PREFIX = "beanstalk.";

    // in
    @Metadata(description = "The priority value set", javaType = "long")
    public static final String PRIORITY = PREFIX + "priority";
    @Metadata(description = "The Job delay in seconds", javaType = "Integer")
    public static final String DELAY = PREFIX + "delay";
    @Metadata(description = "The Job time to run in seconds. (when 0, the beanstalkd daemon raises it to 1 automatically, see Beanstalk protocol)",
              javaType = "Integer")
    public static final String TIME_TO_RUN = PREFIX + "timeToRun";

    // in/out
    @Metadata(description = "Job ID", javaType = "long")
    public static final String JOB_ID = PREFIX + "jobId";

    // out
    @Metadata(description = "The flag indicating if the operation was a success or not", javaType = "Boolean")
    public static final String RESULT = PREFIX + "result";

    // other info
    @Metadata(description = "The name of the tube that contains this job", javaType = "String")
    public static final String TUBE = PREFIX + "tube";
    @Metadata(description = "“ready” or “delayed” or “reserved” or “buried” (must be “reserved”)", javaType = "String")
    public static final String STATE = PREFIX + "state";
    @Metadata(description = "The time in seconds since the put command that created this job", javaType = "Integer")
    public static final String AGE = PREFIX + "age";
    @Metadata(description = "The number of seconds left until the server puts this job into the ready queue",
              javaType = "Integer")
    public static final String TIME_LEFT = PREFIX + "time-left";
    @Metadata(description = "The number of times this job has timed out during a reservation", javaType = "Integer")
    public static final String TIMEOUTS = PREFIX + "timeouts";
    @Metadata(description = "The number of times a client has released this job from a reservation", javaType = "Integer")
    public static final String RELEASES = PREFIX + "releases";
    @Metadata(description = "The number of times this job has been buried", javaType = "Integer")
    public static final String BURIES = PREFIX + "buries";
    @Metadata(description = "The number of times this job has been kicked", javaType = "Integer")
    public static final String KICKS = PREFIX + "kicks";

    private Headers() {
    }

}
