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
package org.apache.camel.component.wolfdefender;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/** Owns the local tokenizer and ONNX session; invoked by a single worker per expert. */
class WolfDefenderInference implements AutoCloseable {
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    static WolfDefenderInference load(Path directory, int threads) throws Exception {
        verify(directory.resolve("onnx/onnx_fp32/model.onnx"),
                "49455de2407c134dd136c64ca38d67ca17f8426e99fe5c6e286d69af9932993f");
        verify(directory.resolve("tokenizer.json"),
                "7e426c3929b44e6ab4c931770b5f22b913280633f5a1c67c81e9ad64decef55c");
        // This exact configuration defines class 0 = benign and class 1 = injection.
        verify(directory.resolve("config.json"),
                "b5bfba7b100b4b1aa361e8160e5593695164d81ca09b47f3b26561332b520218");
        return new WolfDefenderInference(
                directory.resolve("onnx/onnx_fp32/model.onnx"),
                directory.resolve("tokenizer.json"), threads);
    }

    WolfDefenderInference(Path model, Path tokenizerFile, int threads) throws Exception {
        environment = OrtEnvironment.getEnvironment();
        OrtSession opened = null;
        HuggingFaceTokenizer loaded = null;
        try {
            try (var options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(threads);
                options.setInterOpNumThreads(1);
                options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
                options.addConfigEntry("session.intra_op.allow_spinning", "0");
                opened = environment.createSession(model.toString(), options);
            }
            validateGraph(opened);
            loaded = HuggingFaceTokenizer.newInstance(tokenizerFile,
                    Map.of("truncation", "false", "padding", "false", "addSpecialTokens", "true"));
        } catch (Exception | LinkageError failure) {
            if (loaded != null) {
                loaded.close();
            }
            if (opened != null) {
                opened.close();
            }
            throw failure;
        }
        session = opened;
        tokenizer = loaded;
    }

    static void verify(Path file, String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int size;
            while ((size = stream.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Wolf-Defender artifact loading interrupted");
                }
                digest.update(buffer, 0, size);
            }
        }
        if (!expected.equals(HexFormat.of().formatHex(digest.digest()))) {
            throw new WolfDefenderException("Artifact checksum mismatch: " + file.getFileName());
        }
    }

    private static void validateGraph(OrtSession session) throws OrtException {
        if (!session.getInputNames().equals(Set.of("input_ids", "attention_mask"))
                || !session.getOutputNames().equals(Set.of("logits"))) {
            throw new WolfDefenderException("Expected input_ids, attention_mask and logits tensors");
        }
        for (NodeInfo node : session.getInputInfo().values()) {
            if (!(node.getInfo() instanceof TensorInfo tensor) || tensor.type != OnnxJavaType.INT64
                    || tensor.getShape().length != 2 || tensor.getShape()[0] != -1 || tensor.getShape()[1] != -1) {
                throw new WolfDefenderException("Expected dynamic INT64 [batch, sequence] inputs");
            }
        }
        if (!(session.getOutputInfo().get("logits").getInfo() instanceof TensorInfo tensor)
                || tensor.type != OnnxJavaType.FLOAT || !Arrays.equals(tensor.getShape(), new long[] { -1, 2 })) {
            throw new WolfDefenderException("Expected FLOAT [batch, 2] logits");
        }
    }

    void validateInput(String text, int maxTokens, Call call) {
        encode(text, maxTokens, call);
    }

    private Encoding encode(String text, int maxTokens, Call call) {
        call.checkCancelled();
        Encoding encoded = tokenizer.encode(text);
        if (encoded.getIds().length > maxTokens) {
            throw new WolfDefenderException("Input exceeds maxTokens=" + maxTokens + " including special tokens");
        }
        call.checkCancelled();
        return encoded;
    }

    double evaluate(String text, int maxTokens, Call call) throws Exception {
        Encoding encoded = encode(text, maxTokens, call);
        long[] ids = encoded.getIds();
        try (var input = OnnxTensor.createTensor(environment, new long[][] { ids });
             var mask = OnnxTensor.createTensor(environment, new long[][] { encoded.getAttentionMask() })) {
            OrtSession.RunOptions options = call.open();
            try (var result = session.run(Map.of("input_ids", input, "attention_mask", mask), options)) {
                call.checkCancelled();
                return injectionProbability(result.get("logits").orElseThrow().getValue());
            } finally {
                call.close();
            }
        }
    }

    static double injectionProbability(Object output) {
        if (!(output instanceof float[][] logits) || logits.length != 1 || logits[0] == null
                || logits[0].length != 2 || !Float.isFinite(logits[0][0]) || !Float.isFinite(logits[0][1])) {
            throw new WolfDefenderException("Expected finite logits with shape [1, 2]");
        }
        double maximum = Math.max(logits[0][0], logits[0][1]);
        double benign = Math.exp((double) logits[0][0] - maximum);
        double injection = Math.exp((double) logits[0][1] - maximum);
        return injection / (benign + injection);
    }

    @Override
    public void close() throws OrtException {
        try {
            tokenizer.close();
        } finally {
            session.close();
        }
        // OrtEnvironment is a process-wide singleton, owned by ONNX Runtime.
    }

    /** Synchronizes cancellation with the lifetime of the native RunOptions handle. */
    static final class Call implements AutoCloseable {
        private boolean cancelled;
        private Thread runner;
        private OrtSession.RunOptions options;

        synchronized void start() {
            checkCancelled();
            runner = Thread.currentThread();
        }

        synchronized void finish() {
            runner = null;
        }

        synchronized void checkCancelled() {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Wolf-Defender evaluation cancelled");
            }
        }

        synchronized OrtSession.RunOptions open() throws OrtException {
            checkCancelled();
            options = new OrtSession.RunOptions();
            return options;
        }

        synchronized void cancel() throws OrtException {
            cancelled = true;
            try {
                if (options != null) {
                    options.setTerminate(true);
                }
            } finally {
                if (runner != null) {
                    runner.interrupt();
                }
            }
        }

        @Override
        public synchronized void close() {
            if (options != null) {
                options.close();
                options = null;
            }
        }
    }
}
