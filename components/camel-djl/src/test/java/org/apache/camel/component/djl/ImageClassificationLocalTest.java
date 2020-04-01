package org.apache.camel.component.djl;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.basicmodelzoo.basic.Mlp;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.translator.ImageClassificationTranslator;
import ai.djl.translate.Pipeline;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public class ImageClassificationLocalTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ImageClassificationLocalTest.class);

    private static final String MODEL_DIR = "src/test/resources/models/mnist";
    private static final String MODEL_NAME = "mlp";

    @Test
    public void testDJL() throws Exception {
        LOG.info("Read and load local model");
        loadLocalModel();

        LOG.info("Starting route to infer");
        context.createProducerTemplate().sendBody("controlbus:route?routeId=infer&action=start", null);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(98);
        mock.await();
        long count = mock.getExchanges().stream().filter(exchange -> exchange.getIn().getBody(Boolean.class)).count();
        assertEquals(98, count);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/resources/data/mnist?recursive=true&noop=true")
                        .routeId("infer").autoStartup(false)
                        .convertBodyTo(byte[].class)
                        .to("djl:cv/image_classification?model=MyModel&translator=MyTranslator")
                        .log("${header.CamelFileName} = ${body}")
                        .process(exchange -> {
                            String filename = exchange.getIn().getHeader("CamelFileName", String.class);
                            Map<String, Float> result = exchange.getIn().getBody(Map.class);
                            String max = Collections.max(result.entrySet(), Comparator.comparingDouble(Map.Entry::getValue)).getKey();
                            exchange.getIn().setBody(filename.startsWith(max));
                        })
                        .log("${header.CamelFileName} = ${body}")
                        .to("mock:result");
            }
        };
    }

    private void loadLocalModel() throws IOException, MalformedModelException, TranslateException {
        // create deep learning model
        Model model = Model.newInstance();
        model.setBlock(new Mlp(28 * 28, 10, new int[]{128, 64}));
        model.load(Paths.get(MODEL_DIR), MODEL_NAME);
        // create translator for pre-processing and postprocessing
        ImageClassificationTranslator.Builder builder = ImageClassificationTranslator.builder();
        builder.setSynsetArtifactName("synset.txt");
        builder.setPipeline(new Pipeline(new ToTensor()));
        builder.optApplySoftmax(true);
        ImageClassificationTranslator translator = new ImageClassificationTranslator(builder);

        // Bind model beans
        context.getRegistry().bind("MyModel", model);
        context.getRegistry().bind("MyTranslator", translator);
    }
}
