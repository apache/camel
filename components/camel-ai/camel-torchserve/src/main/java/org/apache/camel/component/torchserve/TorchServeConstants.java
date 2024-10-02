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

    @Metadata(description = "Additional options for the register operation.",
              javaType = "org.apache.camel.component.torchserve.client.model.RegisterOptions")
    String REGISTER_OPTIONS = "CamelTorchServeRegisterOptions";

    @Metadata(description = "Additional options for the scale-worker operation.",
              javaType = "org.apache.camel.component.torchserve.client.model.ScaleWorkerOptions")
    String SCALE_WORKER_OPTIONS = "CamelTorchServeScaleWorkerOptions";

    @Metadata(description = "Additional options for the unregister operation.",
              javaType = "org.apache.camel.component.torchserve.client.model.UnregisterOptions")
    String UNREGISTER_OPTIONS = "CamelTorchServeUnrsegisterOptions";

}
