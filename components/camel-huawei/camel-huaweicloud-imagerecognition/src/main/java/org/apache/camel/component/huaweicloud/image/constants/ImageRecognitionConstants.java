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

package org.apache.camel.component.huaweicloud.image.constants;

public interface ImageRecognitionConstants {

    String OPERATION_TAG_RECOGNITION = "tagRecognition";

    String OPERATION_CELEBRITY_RECOGNITION = "celebrityRecognition";

    int DEFAULT_TAG_LIMIT = 50;

    String TAG_LANGUAGE_ZH = "zh";

    String TAG_LANGUAGE_EN = "en";

    float TAG_RECOGNITION_THRESHOLD_MAX = 100;

    float CELEBRITY_RECOGNITION_THRESHOLD_MAX = 1;

    float DEFAULT_TAG_RECOGNITION_THRESHOLD = 60;

    float DEFAULT_CELEBRITY_RECOGNITION_THRESHOLD = 0.48f;

}
