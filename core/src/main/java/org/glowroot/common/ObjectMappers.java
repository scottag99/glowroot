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
package org.glowroot.common;

import java.io.IOException;
import java.util.Locale;

import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.EnsuresNonNull;
import checkers.nullness.quals.Nullable;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.markers.Static;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
// unfortunately this class cannot be used by test-container since sometimes this class exposes
// unshaded jackson types (in IDE) and sometimes it exposes shaded jackson types (in maven build),
// therefore there is a mostly duplicate class under test-container
@Static
public class ObjectMappers {

    private static final Logger logger = LoggerFactory.getLogger(ObjectMappers.class);

    private ObjectMappers() {}

    public static ObjectMapper create() {
        return new ObjectMapper().registerModule(EnumModule.create());
    }

    public static <T extends /*@Nullable*/Object> T readRequiredValue(
            @ReadOnly ObjectMapper mapper, String content, Class<T> type) throws IOException {
        T value = mapper.readValue(content, type);
        if (value == null) {
            throw new JsonMappingException("Content is json null");
        }
        return value;
    }

    public static <T extends /*@Nullable*/Object> T treeToRequiredValue(
            @ReadOnly ObjectMapper mapper, TreeNode n, Class<T> type)
            throws JsonProcessingException {
        T value = mapper.treeToValue(n, type);
        if (value == null) {
            throw new JsonMappingException("Node is json null");
        }
        return value;
    }

    @EnsuresNonNull("#1")
    public static <T extends /*@Nullable*/Object> void checkRequiredProperty(T reference,
            String fieldName) throws JsonMappingException {
        if (reference == null) {
            throw new JsonMappingException("Null value not allowed for field: " + fieldName);
        }
    }

    @SuppressWarnings("serial")
    private static class EnumModule extends SimpleModule {
        private static EnumModule create() {
            EnumModule module = new EnumModule();
            module.addSerializer(Enum.class, new EnumSerializer());
            return module;
        }
        @Override
        public void setupModule(SetupContext context) {
            super.setupModule(context);
            context.addDeserializers(new Deserializers.Base() {
                @Override
                public JsonDeserializer<?> findEnumDeserializer(Class<?> type,
                        DeserializationConfig config, BeanDescription beanDesc)
                        throws JsonMappingException {
                    return new EnumDeserializer(type);
                }
            });
        }
    }

    private static class EnumSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(@Nullable Object value, JsonGenerator jgen,
                SerializerProvider provider) throws IOException {
            if (value == null) {
                jgen.writeNull();
            } else if (value instanceof Enum) {
                jgen.writeString(((Enum<?>) value).name().toLowerCase(Locale.ENGLISH));
            } else {
                logger.error("unexpected value class: {}", value.getClass());
            }
        }
    }

    private static class EnumDeserializer extends JsonDeserializer<Enum<?>> {

        private final Class<?> enumClass;
        private final ImmutableMap<String, Enum<?>> enumMap;

        public EnumDeserializer(Class<?> enumClass) {
            this.enumClass = enumClass;
            if (enumClass.isEnum()) {
                ImmutableMap.Builder<String, Enum<?>> theEnumMap = ImmutableMap.builder();
                Object[] enumConstants = enumClass.getEnumConstants();
                if (enumConstants != null) {
                    for (Object enumConstant : enumConstants) {
                        if (enumConstant instanceof Enum) {
                            Enum<?> constant = (Enum<?>) enumConstant;
                            theEnumMap.put(constant.name().toLowerCase(Locale.ENGLISH), constant);
                        } else {
                            logger.error("unexpected constant class: {}", enumConstant.getClass());
                        }
                    }
                }
                this.enumMap = theEnumMap.build();
            } else {
                logger.error("unexpected class: {}", enumClass);
                this.enumMap = ImmutableMap.of();
            }
        }

        @Override
        @Nullable
        public Enum<?> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String text = jp.getText();
            Enum<?> constant = enumMap.get(text);
            if (constant == null) {
                logger.warn("enum constant {} not found in enum type {}", text,
                        enumClass.getName());
            }
            return constant;
        }
    }
}
