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
package org.glowroot.tests;

import java.util.Iterator;
import java.util.Map.Entry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.Containers;
import org.glowroot.container.AppUnderTest;
import org.glowroot.container.Container;
import org.glowroot.container.trace.Trace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class TraceAttributesTest {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.getSharedContainer();
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
    public void shouldReadTraceAttributesInOrder() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ShouldGenerateTraceWithNestedSpans.class);
        // then
        Trace trace = container.getTraceService().getLastTrace();
        Iterator<Entry<String, String>> i = trace.getAttributes().entrySet().iterator();
        Entry<String, String> entry = i.next();
        assertThat(entry.getKey()).isEqualTo("ax");
        assertThat(entry.getValue()).isEqualTo("bx");
        entry = i.next();
        assertThat(entry.getKey()).isEqualTo("z");
        assertThat(entry.getValue()).isEqualTo("zz");
        entry = i.next();
        assertThat(entry.getKey()).isEqualTo("y");
        assertThat(entry.getValue()).isEqualTo("yy");
        entry = i.next();
        assertThat(entry.getKey()).isEqualTo("x");
        assertThat(entry.getValue()).isEqualTo("xx");
        entry = i.next();
        assertThat(entry.getKey()).isEqualTo("w");
        assertThat(entry.getValue()).isEqualTo("ww");
    }

    public static class ShouldGenerateTraceWithNestedSpans implements AppUnderTest {
        @Override
        public void executeApp() {
            new LevelOne().call("ax", "bx");
        }
    }
}
