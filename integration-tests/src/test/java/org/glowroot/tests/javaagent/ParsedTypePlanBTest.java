/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.tests.javaagent;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import com.google.common.io.Resources;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.Containers;
import org.glowroot.container.AppUnderTest;
import org.glowroot.container.Container;
import org.glowroot.weaving.ParsedTypeCache;
import org.glowroot.weaving.TypeNames;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class ParsedTypePlanBTest {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.getSharedJavaagentContainer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.checkAndReset();
    }

    @Test
    public void shouldNotLogWarningInParsedTypeCachePlanB() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ShouldNotLogWarningInParsedTypeCachePlanB.class);
        // then
        // container close will validate that there were no unexpected warnings or errors
    }

    @Test
    public void shouldLogWarningInParsedTypeCachePlanB() throws Exception {
        // given
        container.addExpectedLogMessage(ParsedTypeCache.class.getName(), "could not find resource "
                + TypeNames.toInternal(Y.class.getName()) + ".class");
        // when
        container.executeAppUnderTest(ShouldLogWarningInParsedTypeCachePlanB.class);
        // then
    }

    public static class ShouldNotLogWarningInParsedTypeCachePlanB implements AppUnderTest {
        @Override
        public void executeApp() throws Exception {
            Class.forName(Z.class.getName(), true, new DelegatingClassLoader());
        }
    }

    public static class ShouldLogWarningInParsedTypeCachePlanB implements AppUnderTest {
        @Override
        public void executeApp() throws Exception {
            Class.forName(Z.class.getName(), true, new DelegatingClassLoader2());
        }
    }

    public static class DelegatingClassLoader extends ClassLoader {
        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {

            if (name.equals(Z.class.getName())) {
                try {
                    return load(name);
                } catch (IOException e) {
                    throw new ClassNotFoundException("Error loading class", e);
                }
            } else {
                return DelegatingClassLoader.class.getClassLoader().loadClass(name);
            }
        }
        protected Class<?> load(String name) throws IOException {
            byte[] bytes = Resources.toByteArray(Resources.getResource(TypeNames.toInternal(name)
                    + ".class"));
            return super.defineClass(name, bytes, 0, bytes.length);
        }
        @Override
        public URL getResource(String name) {
            // don't load .class files as resources
            return null;
        }
        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            // don't load .class files as resources
            return null;
        }
    }

    public static class DelegatingClassLoader2 extends DelegatingClassLoader {
        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {

            if (name.equals(Y.class.getName()) || name.equals(Z.class.getName())) {
                try {
                    return load(name);
                } catch (IOException e) {
                    throw new ClassNotFoundException("Error loading class", e);
                }
            } else {
                return DelegatingClassLoader.class.getClassLoader().loadClass(name);
            }
        }
    }

    public static class Y {}

    public static class Z extends Y {}
}
