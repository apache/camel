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
package org.apache.camel.support.service;

import org.apache.camel.StatefulService;

/**
 * A useful base class which ensures that a service is only initialized once and
 * provides some helper methods for enquiring of its status.
 * <p/>
 * Implementations can extend this base class and implement {@link org.apache.camel.SuspendableService}
 * in case they support suspend/resume.
 * <p/>
 * <b>Important: </b> You should override the lifecycle methods that start with <tt>do</tt>, eg {@link #doStart()}},
 * {@link #doStop()}, etc. where you implement your logic. The methods {@link #start()}, {@link #stop()} should
 * <b>NOT</b> be overridden as they are used internally to keep track of the state of this service and properly
 * invoke the operation in a safe manner.
 */
public abstract class ServiceSupport extends BaseService implements StatefulService {

}
