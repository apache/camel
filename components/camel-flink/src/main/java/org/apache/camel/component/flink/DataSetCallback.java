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

package org.apache.camel.component.flink;

import org.apache.flink.api.java.DataSet;

/**
 * Generic block of code with parameters which can be executed against DataSet and return results.
 *
 * @param      <T> results type
 * @deprecated     The DataSet API is deprecated since Flink 1.12. Use the DataStream API with bounded streams instead.
 *                 See the Flink migration guide for details on migrating from DataSet to DataStream API. This class
 *                 will be maintained for backward compatibility but may be removed in future versions.
 */
@Deprecated(since = "4.16.0")
public interface DataSetCallback<T> {

    T onDataSet(DataSet ds, Object... payloads);
}
