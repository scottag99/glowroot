/*
 * Copyright 2013-2014 the original author or authors.
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
package org.glowroot.api;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class BeansTest {

    @Test
    public void shouldCallGetterMethod() {
        String value = (String) Beans.value(new SomeObject(), "one");
        assertThat(value).isEqualTo("1");
    }

    @Test
    public void shouldCallBooleanGetterMethod() {
        boolean value = (Boolean) Beans.value(new SomeObject(), "two");
        assertThat(value).isTrue();
    }

    @Test
    public void shouldCallNonGetterMethod() {
        String value = (String) Beans.value(new SomeObject(), "three");
        assertThat(value).isEqualTo("3");
    }

    @Test
    public void shouldGetField() {
        String value = (String) Beans.value(new SomeObject(), "four");
        assertThat(value).isEqualTo("4");
    }

    @Test
    public void shouldCallMethodOnPackagePrivateClass() {
        List<String> list = Lists.newArrayList();
        list = Collections.synchronizedList(list);
        int size = (Integer) Beans.value(list, "size");
        assertThat(size).isEqualTo(0);
    }

    @SuppressWarnings("unused")
    private static class SomeObject {

        private final String one = "distraction";
        private final String two = "distraction";
        private final String three = "distraction";
        private final String four = "4";

        public String getOne() {
            return "1";
        }

        public String isOne() {
            return "distraction";
        }

        public String one() {
            return "distraction";
        }

        public boolean isTwo() {
            return true;
        }

        public boolean two() {
            return false;
        }

        public String three() {
            return "3";
        }
    }
}
