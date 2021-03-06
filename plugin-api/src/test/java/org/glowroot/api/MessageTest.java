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
package org.glowroot.api;

import org.junit.Test;

import org.glowroot.api.internal.ReadableMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class MessageTest {

    @Test
    public void shouldFormatConstant() {
        ReadableMessage message = (ReadableMessage) Message.from("constant");
        assertThat(message.getText()).isEqualTo("constant");
    }

    @Test
    public void shouldFormatSingle() {
        ReadableMessage message = (ReadableMessage) Message.from("{}", "test");
        assertThat(message.getText()).isEqualTo("test");
    }

    @Test
    public void shouldFormatSinglePlus() {
        ReadableMessage message = (ReadableMessage) Message.from("one {} two", "test");
        assertThat(message.getText()).isEqualTo("one test two");
    }

    @Test
    public void shouldFormatMultiple() {
        ReadableMessage message =
                (ReadableMessage) Message.from("one {} two {}{}", "test", "2", "3");
        assertThat(message.getText()).isEqualTo("one test two 23");
    }

    @Test
    public void shouldFormatConstantWithEmptyMethodBody() {
        ReadableMessage message = (ReadableMessage) Message.from("public void run() {}");
        assertThat(message.getText()).isEqualTo("public void run() {}");
    }
}
