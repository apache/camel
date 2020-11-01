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
package org.apache.camel.spi;

import java.io.Closeable;

/**
 * A marker interface for a service, or other kind of process that is only used during bootstrapping Camel. After the
 * bootstrap is complete the {@link #close()} method is invoked which allows to do some cleanup processes such as
 * clearing internal caches, maps etc to clear up memory etc.
 */
public interface BootstrapCloseable extends Closeable {

}
