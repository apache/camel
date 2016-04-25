/**
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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.google.common.truth.Truth;
import org.apache.camel.component.flink.annotations.AnnotatedDataSetCallback;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.junit.Test;

public class FlinkProducerTest extends CamelTestSupport {

    static ExecutionEnvironment executionEnvironment = Flinks.createExecutionEnvironment();

    String flinkUri = "flink:dataSet?dataSet=#myDataSet";

    int numberOfLinesInTestFile = 19;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        registry.bind("myDataSet", executionEnvironment.readTextFile("src/test/resources/testds.txt"));

        registry.bind("countLinesContaining", new DataSetCallback() {
            @Override
            public Object onDataSet(DataSet ds, Object... payloads) {
                try {
                    return ds.count();
                } catch (Exception e) {
                    return null;
                }
            }
        });
        return registry;
    }

    @Test
    public void shouldExecuteDataSetCallback() {
        Long linesCount = template.requestBodyAndHeader(flinkUri, null, FlinkConstants.FLINK_DATASET_CALLBACK_HEADER, new DataSetCallback() {
            @Override
            public Object onDataSet(DataSet ds, Object... payloads) {
                try {
                    return ds.count();
                } catch (Exception e) {
                    return null;
                }
            }
        }, Long.class);

        Truth.assertThat(linesCount).isEqualTo(numberOfLinesInTestFile);
    }

    @Test
    public void shouldExecuteDataSetCallbackWithSinglePayload() {
        Long linesCount = template.requestBodyAndHeader(flinkUri, 10, FlinkConstants.FLINK_DATASET_CALLBACK_HEADER, new DataSetCallback() {
            @Override
            public Object onDataSet(DataSet ds, Object... payloads) {
                try {
                    return ds.count() * (int) payloads[0];
                } catch (Exception e) {
                    return null;
                }
            }
        }, Long.class);

        Truth.assertThat(linesCount).isEqualTo(numberOfLinesInTestFile * 10);
    }

    @Test
    public void shouldExecuteDataSetCallbackWithPayloads() {
        Long linesCount = template.requestBodyAndHeader(flinkUri, Arrays.<Integer>asList(10, 10), FlinkConstants.FLINK_DATASET_CALLBACK_HEADER, new DataSetCallback() {
            @Override
            public Object onDataSet(DataSet ds, Object... payloads) {
                try {
                    return ds.count() * (int) payloads[0] * (int) payloads[1];
                } catch (Exception e) {
                    return null;
                }
            }
        }, Long.class);

        Truth.assertThat(linesCount).isEqualTo(numberOfLinesInTestFile * 10 * 10);
    }

    @Test
    public void shouldUseTransformationFromRegistry() {
        Long linesCount = template.requestBody(flinkUri + "&dataSetCallback=#countLinesContaining", null, Long.class);
        Truth.assertThat(linesCount).isGreaterThan(0L);
    }

    @Test
    public void shouldExecuteVoidCallback() throws IOException {
        final File output = File.createTempFile("camel", "flink");
        output.delete();

        template.sendBodyAndHeader(flinkUri, null, FlinkConstants.FLINK_DATASET_CALLBACK_HEADER, new VoidDataSetCallback() {
            @Override
            public void doOnDataSet(DataSet ds, Object... payloads) {
                ds.writeAsText(output.getAbsolutePath());
            }
        });

        Truth.assertThat(output.length()).isAtLeast(0L);
    }

    @Test
    public void shouldExecuteAnnotatedCallback() {
        DataSetCallback dataSetCallback = new AnnotatedDataSetCallback(new Object() {
            @org.apache.camel.component.flink.annotations.DataSetCallback
            Long countLines(DataSet<String> textFile) {
                try {
                    return textFile.count();
                } catch (Exception e) {
                    return null;
                }
            }
        });

        long pomLinesCount = template.requestBodyAndHeader(flinkUri, null, FlinkConstants.FLINK_DATASET_CALLBACK_HEADER, dataSetCallback, Long.class);

        Truth.assertThat(pomLinesCount).isEqualTo(19);
    }

    @Test
    public void shouldExecuteAnnotatedVoidCallback() throws IOException {
        final File output = File.createTempFile("camel", "flink");
        output.delete();

        DataSetCallback dataSetCallback = new AnnotatedDataSetCallback(new Object() {
            @org.apache.camel.component.flink.annotations.DataSetCallback
            void countLines(DataSet<String> textFile) {
                textFile.writeAsText(output.getAbsolutePath());
            }
        });

        template.sendBodyAndHeader(flinkUri, null, FlinkConstants.FLINK_DATASET_CALLBACK_HEADER, dataSetCallback);

        Truth.assertThat(output.length()).isAtLeast(0L);
    }

    @Test
    public void shouldExecuteAnnotatedCallbackWithParameters() {
        DataSetCallback dataSetCallback = new AnnotatedDataSetCallback(new Object() {
            @org.apache.camel.component.flink.annotations.DataSetCallback
            Long countLines(DataSet<String> textFile, int first, int second) {
                try {
                    return textFile.count() * first * second;
                } catch (Exception e) {
                    return null;
                }
            }
        });

        long pomLinesCount = template.requestBodyAndHeader(flinkUri, Arrays.<Integer>asList(10, 10), FlinkConstants.FLINK_DATASET_CALLBACK_HEADER, dataSetCallback, Long.class);
        Truth.assertThat(pomLinesCount).isEqualTo(numberOfLinesInTestFile * 10 * 10);
    }
}