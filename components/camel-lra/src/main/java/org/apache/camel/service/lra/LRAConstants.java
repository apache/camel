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
package org.apache.camel.service.lra;

public final class LRAConstants {

    public static final String DEFAULT_COORDINATOR_CONTEXT_PATH = "/lra-coordinator";
    public static final String DEFAULT_LOCAL_PARTICIPANT_CONTEXT_PATH = "/lra-participant";

    static final String COORDINATOR_PATH_START = "/start";
    static final String COORDINATOR_PATH_CLOSE = "/close";
    static final String COORDINATOR_PATH_CANCEL = "/cancel";

    static final String PARTICIPANT_PATH_COMPENSATE = "/compensate";
    static final String PARTICIPANT_PATH_COMPLETE = "/complete";

    static final String HEADER_LINK = "Link";
    static final String HEADER_TIME_LIMIT = "TimeLimit";

    static final String URL_COMPENSATION_KEY = "Camel-Saga-Compensate";
    static final String URL_COMPLETION_KEY = "Camel-Saga-Complete";

    private LRAConstants() {
    }

}
