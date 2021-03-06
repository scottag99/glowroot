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
package org.glowroot.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import checkers.igj.quals.Immutable;
import checkers.igj.quals.ReadOnly;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter.Lf2SpacesIndenter;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.common.ObjectMappers;
import org.glowroot.config.JsonViews.FileView;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Immutable
class ConfigMapper {

    @ReadOnly
    private static final Logger logger = LoggerFactory.getLogger(ConfigMapper.class);

    private static final String NEWLINE = System.getProperty("line.separator");

    @ReadOnly
    private static final ObjectMapper mapper = ObjectMappers.create();

    private static final String GENERAL = "general";
    private static final String COARSE_PROFILING = "coarse-profiling";
    private static final String FINE_PROFILING = "fine-profiling";
    private static final String USER = "user";
    private static final String STORAGE = "storage";
    private static final String USER_INTERFACE = "ui";
    private static final String ADVANCED = "advanced";
    private static final String PLUGINS = "plugins";
    private static final String POINTCUTS = "pointcuts";

    private final ImmutableList<PluginDescriptor> pluginDescriptors;

    ConfigMapper(@ReadOnly List<PluginDescriptor> pluginDescriptors) {
        this.pluginDescriptors = ImmutableList.copyOf(pluginDescriptors);
    }

    Config readValue(String content) throws IOException {
        ObjectNode rootNode = (ObjectNode) mapper.readTree(content);
        GeneralConfig generalConfig = readGeneralNode(rootNode);
        CoarseProfilingConfig coarseProfilingConfig = readCoarseProfilingNode(rootNode);
        FineProfilingConfig fineProfilingConfig = readFineProfilingNode(rootNode);
        UserOverridesConfig userOverridesConfig = readUserNode(rootNode);
        StorageConfig storageConfig = readStorageNode(rootNode);
        UserInterfaceConfig userInterfaceConfig = readUserInterfaceNode(rootNode);
        AdvancedConfig advancedConfig = readAdvancedNode(rootNode);
        Map<String, ObjectNode> pluginNodes = createPluginNodes(rootNode);
        ImmutableList<PluginConfig> pluginConfigs =
                createPluginConfigs(pluginNodes, pluginDescriptors);
        ImmutableList<PointcutConfig> pointcutConfigs = createPointcutConfigs(rootNode);
        return new Config(generalConfig, coarseProfilingConfig, fineProfilingConfig,
                userOverridesConfig, storageConfig, userInterfaceConfig, advancedConfig,
                pluginConfigs, pointcutConfigs);
    }

    static void writeValue(File configFile, Config config) throws IOException {
        Files.write(writeValueAsString(config), configFile, Charsets.UTF_8);
    }

    static String writeValueAsString(Config config) throws IOException {
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(Lf2SpacesIndenter.instance);
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb))
                .setPrettyPrinter(prettyPrinter);
        // this view will exclude version properties
        ObjectWriter writer = mapper.writerWithView(FileView.class);
        jg.writeStartObject();
        jg.writeFieldName(GENERAL);
        writer.writeValue(jg, config.getGeneralConfig());
        jg.writeFieldName(COARSE_PROFILING);
        writer.writeValue(jg, config.getCoarseProfilingConfig());
        jg.writeFieldName(FINE_PROFILING);
        writer.writeValue(jg, config.getFineProfilingConfig());
        jg.writeFieldName(USER);
        writer.writeValue(jg, config.getUserOverridesConfig());
        jg.writeFieldName(STORAGE);
        writer.writeValue(jg, config.getStorageConfig());
        jg.writeFieldName(USER_INTERFACE);
        writer.writeValue(jg, config.getUserInterfaceConfig());
        jg.writeFieldName(ADVANCED);
        writer.writeValue(jg, config.getAdvancedConfig());

        jg.writeArrayFieldStart(PLUGINS);
        for (PluginConfig pluginConfig : config.getPluginConfigs()) {
            writer.writeValue(jg, pluginConfig);
        }
        jg.writeEndArray();
        jg.writeArrayFieldStart(POINTCUTS);
        for (PointcutConfig pointcutConfig : config.getPointcutConfigs()) {
            writer.writeValue(jg, pointcutConfig);
        }
        jg.writeEndArray();
        jg.writeEndObject();
        jg.close();
        // newline is not required, just a personal preference
        return sb.toString() + NEWLINE;
    }

    private static GeneralConfig readGeneralNode(ObjectNode rootNode)
            throws IOException {
        ObjectNode configNode = (ObjectNode) rootNode.get(GENERAL);
        GeneralConfig defaultConfig = GeneralConfig.getDefault();
        if (configNode == null) {
            return defaultConfig;
        } else {
            GeneralConfig.Overlay overlay = GeneralConfig.overlay(defaultConfig);
            mapper.readerForUpdating(overlay).readValue(configNode);
            return overlay.build();
        }
    }

    private static CoarseProfilingConfig readCoarseProfilingNode(ObjectNode rootNode)
            throws IOException {
        ObjectNode configNode = (ObjectNode) rootNode.get(COARSE_PROFILING);
        CoarseProfilingConfig defaultConfig = CoarseProfilingConfig.getDefault();
        if (configNode == null) {
            return defaultConfig;
        } else {
            CoarseProfilingConfig.Overlay overlay = CoarseProfilingConfig.overlay(defaultConfig);
            mapper.readerForUpdating(overlay).readValue(configNode);
            return overlay.build();
        }
    }

    private static FineProfilingConfig readFineProfilingNode(ObjectNode rootNode)
            throws IOException {
        ObjectNode configNode = (ObjectNode) rootNode.get(FINE_PROFILING);
        FineProfilingConfig defaultConfig = FineProfilingConfig.getDefault();
        if (configNode == null) {
            return defaultConfig;
        } else {
            FineProfilingConfig.Overlay overlay = FineProfilingConfig.overlay(defaultConfig);
            mapper.readerForUpdating(overlay).readValue(configNode);
            return overlay.build();
        }
    }

    private static UserOverridesConfig readUserNode(ObjectNode rootNode) throws IOException {
        ObjectNode configNode = (ObjectNode) rootNode.get(USER);
        UserOverridesConfig defaultConfig = UserOverridesConfig.getDefault();
        if (configNode == null) {
            return defaultConfig;
        } else {
            UserOverridesConfig.Overlay overlay = UserOverridesConfig.overlay(defaultConfig);
            mapper.readerForUpdating(overlay).readValue(configNode);
            return overlay.build();
        }
    }

    private static StorageConfig readStorageNode(ObjectNode rootNode) throws IOException {
        ObjectNode configNode = (ObjectNode) rootNode.get(STORAGE);
        StorageConfig defaultConfig = StorageConfig.getDefault();
        if (configNode == null) {
            return defaultConfig;
        } else {
            StorageConfig.Overlay overlay = StorageConfig.overlay(defaultConfig);
            mapper.readerForUpdating(overlay).readValue(configNode);
            return overlay.build();
        }
    }

    private static UserInterfaceConfig readUserInterfaceNode(ObjectNode rootNode)
            throws IOException {
        ObjectNode configNode = (ObjectNode) rootNode.get(USER_INTERFACE);
        UserInterfaceConfig defaultConfig = UserInterfaceConfig.getDefault();
        if (configNode == null) {
            return defaultConfig;
        } else {
            UserInterfaceConfig.FileOverlay overlay =
                    UserInterfaceConfig.fileOverlay(defaultConfig);
            mapper.readerForUpdating(overlay).readValue(configNode);
            return overlay.build();
        }
    }

    private static AdvancedConfig readAdvancedNode(ObjectNode rootNode) throws IOException {
        ObjectNode configNode = (ObjectNode) rootNode.get(ADVANCED);
        AdvancedConfig defaultConfig = AdvancedConfig.getDefault();
        if (configNode == null) {
            return defaultConfig;
        } else {
            AdvancedConfig.Overlay overlay = AdvancedConfig.overlay(defaultConfig);
            mapper.readerForUpdating(overlay).readValue(configNode);
            return overlay.build();
        }
    }

    @ReadOnly
    private static Map<String, ObjectNode> createPluginNodes(ObjectNode rootNode) {
        ArrayNode pluginsNode = (ArrayNode) rootNode.get(PLUGINS);
        if (pluginsNode == null) {
            return ImmutableMap.of();
        }
        Map<String, ObjectNode> pluginNodes = Maps.newHashMap();
        for (JsonNode pluginNode : pluginsNode) {
            ObjectNode pluginObjectNode = (ObjectNode) pluginNode;
            JsonNode id = pluginObjectNode.get("id");
            if (id == null) {
                logger.warn("error in config.json file, id is missing");
                continue;
            }
            if (!id.isTextual()) {
                logger.warn("error in config.json file, id is not a string");
                continue;
            }
            pluginNodes.put(id.asText(), pluginObjectNode);
        }
        return pluginNodes;
    }

    private static ImmutableList<PluginConfig> createPluginConfigs(
            @ReadOnly Map<String, ObjectNode> pluginNodes,
            @ReadOnly List<PluginDescriptor> pluginDescriptors) throws JsonMappingException {
        ImmutableList.Builder<PluginConfig> pluginConfigs = ImmutableList.builder();
        for (PluginDescriptor pluginDescriptor : pluginDescriptors) {
            ObjectNode pluginConfigNode = pluginNodes.get(pluginDescriptor.getId());
            if (pluginConfigNode == null) {
                pluginConfigs.add(PluginConfig.getDefault(pluginDescriptor));
            } else {
                PluginConfig.Builder builder = new PluginConfig.Builder(pluginDescriptor);
                builder.overlay(pluginConfigNode, true);
                pluginConfigs.add(builder.build());
            }
        }
        return pluginConfigs.build();
    }

    private static ImmutableList<PointcutConfig> createPointcutConfigs(
            ObjectNode rootNode) throws JsonProcessingException {
        JsonNode pointcutsNode = rootNode.get(POINTCUTS);
        if (pointcutsNode == null) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<PointcutConfig> pointcutConfigs = ImmutableList.builder();
        for (JsonNode pointcutNode : pointcutsNode) {
            PointcutConfig pointcutConfig = ObjectMappers.treeToRequiredValue(mapper,
                    pointcutNode, PointcutConfig.class);
            pointcutConfigs.add(pointcutConfig);
        }
        return pointcutConfigs.build();
    }
}
