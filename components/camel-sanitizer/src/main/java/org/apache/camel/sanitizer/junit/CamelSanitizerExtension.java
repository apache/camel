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
package org.apache.camel.sanitizer.junit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.sanitizer.SanitizerTestSession;
import org.apache.camel.sanitizer.SanitizerViolation;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * The JUnit Jupiter extension of the exchange sanitizer, registered by {@link CamelSanitizer}, or for every test class
 * by the automatic detection of JUnit (the CI switch):
 *
 * <pre>
 * mvn test -Djunit.jupiter.extensions.autodetection.enabled=true \
 *     -Djunit.jupiter.extensions.autodetection.include=org.apache.camel.sanitizer.junit.* \
 *     -Dcamel.sanitizer.enabled=true -Dsurefire.rerunFailingTestsCount=0
 * </pre>
 * <p/>
 * Detected automatically for a class without {@link CamelSanitizer}, it acts only when the system property
 * {@code camel.sanitizer.enabled} is {@code true}, so a project that has JUnit detect its own extensions does not
 * switch the sanitizer on by accident.
 * <p/>
 * It opens a {@link SanitizerTestSession} for the test class, and marks the thread that runs the class with it in
 * {@code beforeAll} and {@code beforeEach}. A CamelContext built on that thread gets the sanitizer (unless profile
 * {@code prod} or {@code camel.sanitizer.enabled=false}), and is tied to the class. A violation of such a context is
 * charged to the test that runs when it happens: after the test, the extension fails it with the first violation and
 * the reports of the others, up to ten in all (added as a suppressed exception when the test failed on its own). A
 * violation charged between tests or after the last one, such as a late second callback, fails the class at its end.
 * When the class ends its contexts are untied; a violation of a context that is not tied (a context still running, or
 * one built on another thread) is charged to the classes running when it happens. Every violation is also written to
 * {@code target/camel-sanitizer/<test class>.txt} (or the directory of the system property
 * {@code camel.sanitizer.reportDir}), so a rerun of a failed test cannot hide it.
 * <p/>
 * It uses only the JUnit Jupiter extension API, so it works with camel-test-junit5, camel-test-junit6 and tests that
 * use neither. The tests of one class must run one at a time (the default of JUnit, also when classes run in parallel):
 * the violations of tests of one class that run at the same time cannot be told apart.
 *
 * @since 4.24
 */
public class CamelSanitizerExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE
            = ExtensionContext.Namespace.create(CamelSanitizerExtension.class);
    private static final String SESSION = "session";
    private static final String OWNER = "owner:";
    private static final String MARKED_BEFORE = "markedBefore:";
    private static final String BETWEEN_TESTS = "betweenTests:";
    private static final Object REPORT_LOCK = new Object();
    private static final Set<Path> CLEARED = ConcurrentHashMap.newKeySet();

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!isAnnotated(context.getRequiredTestClass())
                && !"true".equalsIgnoreCase(System.getProperty("camel.sanitizer.enabled"))) {
            // detected automatically for a class without @CamelSanitizer, as JUnit can be told to detect the extensions
            // of a project: it acts only with the CI switch (camel.sanitizer.enabled=true)
            return;
        }
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        // the extension can be registered twice (the annotation and the automatic detection): the first one acts
        if (store.getOrComputeIfAbsent(OWNER + context.getUniqueId(), k -> this) != this) {
            return;
        }
        SanitizerTestSession session = SanitizerTestSession.open(context.getRequiredTestClass().getName());
        store.put(SESSION, session);
        store.put(MARKED_BEFORE + context.getUniqueId(), new SanitizerTestSession[] { session.mark() });
        store.put(BETWEEN_TESTS + context.getUniqueId(), Collections.synchronizedList(new ArrayList<SanitizerViolation>()));
        clear(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        ExtensionContext classContext = classContext(context);
        if (!isOwner(classContext)) {
            return;
        }
        SanitizerTestSession session = session(context);
        // what was charged since the last test (such as a context of the class stopping late) is not this test's
        betweenTests(classContext).addAll(session.drain());
        context.getStore(NAMESPACE).put(MARKED_BEFORE + context.getUniqueId(),
                new SanitizerTestSession[] { session.mark() });
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ExtensionContext classContext = classContext(context);
        if (!isOwner(classContext)) {
            return;
        }
        SanitizerTestSession[] before = context.getStore(NAMESPACE).remove(MARKED_BEFORE + context.getUniqueId(),
                SanitizerTestSession[].class);
        if (before != null) {
            SanitizerTestSession.restore(before[0]);
        }
        SanitizerTestSession session = session(context);
        List<SanitizerViolation> violations = session.drain();
        List<ExpectSanitizerViolation> expected = context.getTestMethod()
                .map(m -> AnnotationSupport.findRepeatableAnnotations(m, ExpectSanitizerViolation.class))
                .orElse(List.of());
        write(context, violations, expected);

        List<SanitizerViolation> unexpected = new ArrayList<>();
        for (SanitizerViolation v : violations) {
            if (expected.stream().noneMatch(e -> matches(e, v))) {
                unexpected.add(v);
            }
        }
        if (!unexpected.isEmpty()) {
            throw new SanitizerViolationError(context.getDisplayName(), unexpected);
        }
        for (ExpectSanitizerViolation e : expected) {
            // not required when the contexts of the class stayed off on purpose (profile prod, pooled exchanges, ...)
            boolean off = !session.isInstalled() && session.isTurnedOff();
            if (!off && violations.stream().noneMatch(v -> matches(e, v))) {
                throw new AssertionError(
                        "The exchange sanitizer did not report the expected violation "
                                         + (e.value().length == 1 ? e.value()[0] : "(one of) " + Arrays.toString(e.value()))
                                         + (e.step().isEmpty() ? "" : " at step " + e.step()) + " during "
                                         + context.getDisplayName()
                                         + (violations.isEmpty() ? "" : "; it reported: " + violations));
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        if (!isOwner(context)) {
            return;
        }
        SanitizerTestSession session = store.get(SESSION, SanitizerTestSession.class);
        // closed first: what is charged before is drained here, what comes later goes to the classes still running
        session.close();
        List<SanitizerViolation> violations = new ArrayList<>(betweenTests(context));
        violations.addAll(session.drain());
        SanitizerTestSession[] before = store.remove(MARKED_BEFORE + context.getUniqueId(), SanitizerTestSession[].class);
        if (before != null) {
            SanitizerTestSession.restore(before[0]);
        }
        write(context, violations, List.of());
        if (!violations.isEmpty()) {
            throw new SanitizerViolationError(context.getDisplayName() + " (outside its tests)", violations);
        }
    }

    /**
     * Whether the class, a superclass (the annotation is inherited) or an enclosing class (for a nested class) has
     * {@link CamelSanitizer}.
     */
    private static boolean isAnnotated(Class<?> testClass) {
        for (Class<?> c = testClass; c != null; c = c.getEnclosingClass()) {
            if (AnnotationSupport.isAnnotated(c, CamelSanitizer.class)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ExpectSanitizerViolation expected, SanitizerViolation violation) {
        return Arrays.asList(expected.value()).contains(violation.getKind())
                && (expected.step().isEmpty() || expected.step().equals(violation.getStepId()));
    }

    /**
     * The context of the test class: the nearest one without a test method (a parameterized or repeated test has a
     * container between the class and each invocation).
     */
    private static ExtensionContext classContext(ExtensionContext context) {
        ExtensionContext c = context;
        while (c.getTestMethod().isPresent() && c.getParent().isPresent()) {
            c = c.getParent().get();
        }
        return c;
    }

    private boolean isOwner(ExtensionContext classContext) {
        return classContext.getStore(NAMESPACE).get(OWNER + classContext.getUniqueId()) == this;
    }

    private static SanitizerTestSession session(ExtensionContext context) {
        return context.getStore(NAMESPACE).get(SESSION, SanitizerTestSession.class);
    }

    /**
     * The file of the violations of the test class: {@code target/camel-sanitizer/<test class>.txt} of the module (the
     * system property {@code basedir}, which surefire sets, or the working directory), or in the directory of
     * {@code camel.sanitizer.reportDir}; {@code null} when there is no such directory.
     */
    private static Path file(ExtensionContext context) {
        String dir = System.getProperty("camel.sanitizer.reportDir");
        Path target = Paths.get(System.getProperty("basedir", ""), "target");
        Path path;
        if (dir != null && !dir.isBlank()) {
            path = Paths.get(dir);
        } else if (Files.isDirectory(target)) {
            path = target.resolve("camel-sanitizer");
        } else {
            return null;
        }
        return path.resolve(context.getTestClass().map(Class::getName).orElse("unknown") + ".txt");
    }

    /**
     * Starts the file of the test class again, once per JVM, so it holds the violations of this run (its reruns
     * included) and not of an earlier build.
     */
    private static void clear(ExtensionContext classContext) {
        Path file = file(classContext);
        // only the first time in this JVM: surefire reruns a failed test class in the same JVM, and the rerun must not
        // hide what the first attempt reported
        if (file != null && CLEARED.add(file.toAbsolutePath())) {
            synchronized (REPORT_LOCK) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    // only a copy
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<SanitizerViolation> betweenTests(ExtensionContext classContext) {
        return classContext.getStore(NAMESPACE).get(BETWEEN_TESTS + classContext.getUniqueId(), List.class);
    }

    /**
     * Writes the violations to {@code target/camel-sanitizer/<test class>.txt} of the module (the system property
     * {@code basedir}, which surefire sets, or the working directory), so a rerun of the test cannot hide them. Nothing
     * is written when there is no {@code target} directory and no {@code camel.sanitizer.reportDir}.
     */
    private static void write(
            ExtensionContext context, List<SanitizerViolation> violations, List<ExpectSanitizerViolation> expected) {
        if (violations.isEmpty()) {
            return;
        }
        Path file = file(context);
        if (file == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (SanitizerViolation v : violations) {
            boolean isExpected = expected.stream().anyMatch(e -> matches(e, v));
            sb.append(isExpected ? "(expected) " : "").append(context.getDisplayName()).append(" (")
                    .append(context.getUniqueId()).append(")\n");
            sb.append(v.getText()).append("\n\n");
        }
        synchronized (REPORT_LOCK) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, sb, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                // the violations still fail the test; the file is only a copy
            }
        }
    }
}
