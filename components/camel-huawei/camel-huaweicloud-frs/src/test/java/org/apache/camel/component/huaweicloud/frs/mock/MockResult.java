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
package org.apache.camel.component.huaweicloud.frs.mock;

import java.util.*;

import com.huaweicloud.sdk.frs.v2.model.ActionsList;
import com.huaweicloud.sdk.frs.v2.model.BoundingBox;
import com.huaweicloud.sdk.frs.v2.model.CompareFace;
import com.huaweicloud.sdk.frs.v2.model.DetectFace;
import com.huaweicloud.sdk.frs.v2.model.LiveDetectRespVideoresult;

public final class MockResult {
    private MockResult() {
    }

    public static List<DetectFace> getFaceDetectionResult() {
        BoundingBox faceBox = new BoundingBox().withWidth(170).withHeight(150).withTopLeftX(30).withTopLeftY(20);
        return Collections.singletonList(new DetectFace().withBoundingBox(faceBox));
    }

    public static CompareFace getCompareFaceResult() {
        BoundingBox faceBox = new BoundingBox().withWidth(170).withHeight(150).withTopLeftX(30).withTopLeftY(20);
        return new CompareFace().withBoundingBox(faceBox);
    }

    public static LiveDetectRespVideoresult getLiveDetectResult() {
        List<ActionsList> actions = new ArrayList<>();
        actions.add(new ActionsList().withAction(1).withConfidence(0.8));
        actions.add(new ActionsList().withAction(3).withConfidence(0.9));

        return new LiveDetectRespVideoresult().withAlive(true).withPicture("test_face_picture_base_64").withActions(actions);
    }
}
