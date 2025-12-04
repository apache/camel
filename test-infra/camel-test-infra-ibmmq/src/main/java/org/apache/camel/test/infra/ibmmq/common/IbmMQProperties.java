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

package org.apache.camel.test.infra.ibmmq.common;

public class IbmMQProperties {
    public static final String IBM_MQ_CONTAINER = "ibm.mq.container";
    public static final String IBM_MQ_CHANNEL = "ibm.mq.channel";
    public static final String IBM_MQ_QMGR_NAME = "ibm.mq.qmgr.name";
    public static final String IBM_MQ_PORT = "ibm.mq.port";

    public static final String DEFAULT_QMGR_NAME = "QM1";
    public static final String DEFAULT_CHANNEL = "DEV.APP.SVRCONN";

    private IbmMQProperties() {}
}
