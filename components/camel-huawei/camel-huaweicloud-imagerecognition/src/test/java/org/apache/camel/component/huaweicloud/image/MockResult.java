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

package org.apache.camel.component.huaweicloud.image;

import java.util.*;

import com.huaweicloud.sdk.image.v2.model.CelebrityRecognitionResultBody;
import com.huaweicloud.sdk.image.v2.model.ImageTaggingItemBody;
import com.huaweicloud.sdk.image.v2.model.ImageTaggingResponseResult;
import com.huaweicloud.sdk.image.v2.model.RunCelebrityRecognitionResponse;
import com.huaweicloud.sdk.image.v2.model.RunImageTaggingResponse;

public final class MockResult {
    public static final String CELEBRITY_RECOGNITION_RESULT_LABEL = "test_label";

    public static final float CELEBRITY_RECOGNITION_RESULT_CONFIDENCE = 0.8f;

    public static final String TAG_RECOGNITION_RESULT_CONFIDENCE = "80";

    public static final String TAG_RECOGNITION_RESULT_TAG = "test_tag";

    public static final String TAG_RECOGNITION_RESULT_TYPE = "test_type";

    private MockResult() {
    }

    public static RunCelebrityRecognitionResponse getCelebrityRecognitionResponse() {
        Map<String, Integer> faceDetailMap = new HashMap<>();
        faceDetailMap.put("w", 300);
        faceDetailMap.put("h", 500);
        faceDetailMap.put("x", 200);
        faceDetailMap.put("y", 100);

        return new RunCelebrityRecognitionResponse().withResult(Collections
                .singletonList(new CelebrityRecognitionResultBody().withConfidence(CELEBRITY_RECOGNITION_RESULT_CONFIDENCE)
                        .withFaceDetail(faceDetailMap)
                        .withLabel(CELEBRITY_RECOGNITION_RESULT_LABEL)));
    }

    public static RunImageTaggingResponse getTagRecognitionResponse() {
        ImageTaggingItemBody tag = new ImageTaggingItemBody().withConfidence(TAG_RECOGNITION_RESULT_CONFIDENCE)
                .withTag(TAG_RECOGNITION_RESULT_TAG)
                .withType(TAG_RECOGNITION_RESULT_TYPE);

        return new RunImageTaggingResponse()
                .withResult(new ImageTaggingResponseResult().withTags(Collections.singletonList(tag)));
    }
}
