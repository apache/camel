package org.apache.camel.component.firebase;

import com.google.firebase.database.DatabaseReference;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.firebase.provider.ConfigurationProvider;
import org.apache.camel.component.firebase.provider.SampleInputProvider;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Before;
import org.junit.Test;

import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests two scenarios: a synchronous and one asynchronous request.
 */
public class FirebaseProducerTest {

    private final ReentrantLock reentrantLock = new ReentrantLock();

    private final Condition wake = reentrantLock.newCondition();

    private SampleInputProvider sampleInputProvider;

    @Before
    public void setUp() throws Exception {
        sampleInputProvider = new SampleInputProvider();
    }

    @Test
    public void whenFirebaseSet_ShouldReceiveMessagesSync() throws Exception {
        startRoute(false, DatabaseReference.class);
    }

    @Test
    public void whenFirebaseSet_ShouldReceiveMessagesAsync() throws Exception {
        startRoute(true, String.class);
    }

    private void startRoute(final boolean async, final Class<?> expectedBodyClass) throws Exception {
        sampleInputProvider.copySampleFile();
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String rootReference = URLEncoder.encode(ConfigurationProvider.createRootReference(), "UTF-8");
                String serviceAccountFile = ConfigurationProvider.createFirebaseConfigLink();
                from(sampleInputProvider.getTargetFolder().toUri().toString())
                        .process(exchange -> {
                            GenericFile file = (GenericFile) exchange.getIn().getBody();
                            String content = new String(Files.readAllBytes(Paths.get(file.getAbsoluteFilePath())), "UTF-8");
                            String[] keyValue = content.split("=");
                            final Message out = exchange.getOut();
                            out.setHeader("firebaseKey", keyValue[0]);
                            out.setBody(keyValue[1].trim());
                        })
                        .to(String.format("firebase://%s?rootReference=%s&serviceAccountFile=%s&async=%b",
                                ConfigurationProvider.createDatabaseUrl(), rootReference, serviceAccountFile, async))
                        .to("log:whenFirebaseSet?level=WARN")
                        .process(exchange1 -> {
                            assertThat(exchange1.getIn().getBody().getClass()).isEqualTo(expectedBodyClass);
                            try{
                                reentrantLock.lock();
                                wake.signal();
                            }
                            finally {
                                reentrantLock.unlock();
                            }
                        });
            }
        });
        context.start();
        try{
            reentrantLock.lock();
            wake.await();
        }
        finally {
            reentrantLock.unlock();
        }
        context.stop();
    }
}