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
package org.apache.camel.component.openai;

import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIMimeTypeHelperTest {

    @Test
    void shouldDetectPdfMimeType() {
        assertThat(MimeTypeHelper.isPdf("application/pdf")).isTrue();
        assertThat(MimeTypeHelper.isPdf("image/png")).isFalse();
    }

    @Test
    void shouldDetectAudioMimeTypes() {
        assertThat(MimeTypeHelper.isAudio("audio/wav")).isTrue();
        assertThat(MimeTypeHelper.isAudio("audio/mpeg")).isTrue();
        assertThat(MimeTypeHelper.isAudio("video/mp4")).isFalse();
        assertThat(MimeTypeHelper.isAudio("application/pdf")).isFalse();
    }

    @Test
    void shouldMapAudioMimeToInputAudioFormat() {
        assertThat(MimeTypeHelper.audioInputFormat("audio/wav"))
                .isEqualTo(ChatCompletionContentPartInputAudio.InputAudio.Format.WAV);
        assertThat(MimeTypeHelper.audioInputFormat("audio/x-wav"))
                .isEqualTo(ChatCompletionContentPartInputAudio.InputAudio.Format.WAV);
        assertThat(MimeTypeHelper.audioInputFormat("audio/mpeg"))
                .isEqualTo(ChatCompletionContentPartInputAudio.InputAudio.Format.MP3);
        assertThat(MimeTypeHelper.audioInputFormat("audio/ogg")).isNull();
    }

    @Test
    void shouldInferAudioFileExtension() {
        assertThat(MimeTypeHelper.audioExtension("audio/wav")).isEqualTo("wav");
        assertThat(MimeTypeHelper.audioExtension("audio/mpeg")).isEqualTo("mp3");
        assertThat(MimeTypeHelper.audioExtension("audio/ogg")).isNull();
    }
}
