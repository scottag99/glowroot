/*
 * Copyright 2013 the original author or authors.
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
package org.glowroot.container.trace;

import java.util.Map;

import checkers.igj.quals.Immutable;
import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Objects;
import dataflow.quals.Pure;

import static org.glowroot.container.common.ObjectMappers.nullToEmpty;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Immutable
public class ErrorMessage extends Message {

    @Nullable
    private final ExceptionInfo exception;

    private ErrorMessage(@Nullable String text, @ReadOnly Map<String, /*@Nullable*/Object> detail,
            @Nullable ExceptionInfo exception) {
        super(text, detail);
        this.exception = exception;
    }

    @Nullable
    public ExceptionInfo getException() {
        return exception;
    }

    @Override
    @Pure
    public String toString() {
        return Objects.toStringHelper(this)
                .add("text", getText())
                .add("detail", getDetail())
                .add("exception", exception)
                .toString();
    }

    @JsonCreator
    static ErrorMessage readValue(
            @JsonProperty("text") @Nullable String text,
            @JsonProperty("detail") @Nullable Map<String, /*@Nullable*/Object> detail,
            @JsonProperty("exception") @Nullable ExceptionInfo exception)
            throws JsonMappingException {
        return new ErrorMessage(text, nullToEmpty(detail), exception);
    }
}
