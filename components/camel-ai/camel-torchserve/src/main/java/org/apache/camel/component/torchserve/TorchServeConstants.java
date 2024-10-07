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
package org.apache.camel.component.torchserve;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel TorchServe component.
 */
public interface TorchServeConstants {

    @Metadata(description = "The name of model.", javaType = "String")
    String MODEL_NAME = "CamelTorchServeModelName";

    @Metadata(description = "The version of model.", javaType = "String")
    String MODEL_VERSION = "CamelTorchServeModelVersion";

    @Metadata(description = "Model archive download url, support local file or HTTP(s) protocol. For S3, consider using pre-signed url.",
              javaType = "String")
    String URL = "CamelTorchServeUrl";

    @Metadata(description = "Additional options for the register operation.",
              javaType = "org.apache.camel.component.torchserve.client.model.RegisterOptions")
    String REGISTER_OPTIONS = "CamelTorchServeRegisterOptions";

    @Metadata(description = "Additional options for the scale-worker operation.",
              javaType = "org.apache.camel.component.torchserve.client.model.ScaleWorkerOptions")
    String SCALE_WORKER_OPTIONS = "CamelTorchServeScaleWorkerOptions";

    @Metadata(description = "Additional options for the unregister operation.",
              javaType = "org.apache.camel.component.torchserve.client.model.UnregisterOptions")
    String UNREGISTER_OPTIONS = "CamelTorchServeUnrsegisterOptions";

    @Metadata(description = "The maximum number of items to return for the list operation. When this value is present, TorchServe does not return more than the specified number of items, but it might return fewer. This value is optional. If you include a value, it must be between 1 and 1000, inclusive. If you do not include a value, it defaults to 100.",
              javaType = "Integer")
    String LIST_LIMIT = "CamelTorchServeListLimit";

    @Metadata(description = "The token to retrieve the next set of results for the list operation. TorchServe provides the token when the response from a previous call has more results than the maximum page size.",
              javaType = "String")
    String LIST_NEXT_PAGE_TOKEN = "CamelTorchServeListNextPageToken";

    @Metadata(description = "Names of metrics to filter.", javaType = "String")
    String METRICS_NAME = "CamelTorchServeMetricsName";

}
