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
package org.apache.camel.util;

import java.util.Properties;

/**
 * This class is an ordered {@link Properties} where the key/values are stored in the order they are added or loaded.
 * <p/>
 * Note: This implementation is only intended as implementation detail for the Camel properties component, and has only
 * been designed to provide the needed functionality. The complex logic for loading properties has been kept from the
 * JDK {@link Properties} class.
 */
public final class OrderedProperties extends BaseOrderedProperties {

}
