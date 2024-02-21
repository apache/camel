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
package org.apache.camel.test.infra.rocketmq.common;

public final class RocketMQProperties {

    public static final String ROCKETMQ_VERSION_PROPERTY = "itest.rocketmq.container.image.version";
    public static final String ROCKETMQ_IMAGE_PROPERTY = "itest.rocketmq.container.image";
    public static final int ROCKETMQ_NAMESRV_PORT = 9876;
    public static final int ROCKETMQ_BROKER1_PORT = 10909;
    public static final int ROCKETMQ_BROKER2_PORT = 10911;
    public static final int ROCKETMQ_BROKER3_PORT = 10912;

    private RocketMQProperties() {

    }
}
