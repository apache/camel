package org.apache.camel.component.jcache.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

@ExtendWith(HazelcastTest.HazelcastTestExtension.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HazelcastTest {

    String value() default "classpath:hazelcast.xml";

    class HazelcastTestExtension implements BeforeAllCallback, AfterAllCallback {
        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            System.setProperty("hazelcast.named.jcache.instance", "false");
            System.setProperty("hazelcast.jcache.provider.type", "member");
        }

        @Override
        public void afterAll(ExtensionContext context) throws Exception {
        }

    }
}
