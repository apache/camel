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
package org.apache.camel.component.google.speechtotext;

import org.apache.camel.spi.Metadata;

public interface GoogleCloudSpeechToTextConstants {
    @Metadata(description = "The operation to perform",
              javaType = "org.apache.camel.component.google.speechtotext.GoogleCloudSpeechToTextOperations")
    String OPERATION = "GoogleCloudSpeechToTextOperation";
    @Metadata(description = "The response object resulting from the Google Cloud Speech-to-Text API invocation",
              javaType = "com.google.cloud.speech.v1.RecognizeResponse")
    String RESPONSE_OBJECT = "GoogleCloudSpeechToTextResponseObject";
}
