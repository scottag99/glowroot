/**
 * Copyright 2011-2012 the original author or authors.
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
package org.informantproject.testkit;

import java.util.List;
import java.util.Map;

import com.google.common.base.Objects;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class Trace {

    private String id;
    private long from;
    private long to;
    private boolean stuck;
    private long duration;
    private boolean completed;
    private List<String> threadNames;
    private String username;
    private List<Span> spans;
    private MergedStackTreeNode mergedStackTree;

    public String getId() {
        return id;
    }
    public long getFrom() {
        return from;
    }
    public long getTo() {
        return to;
    }
    public boolean isStuck() {
        return stuck;
    }
    public long getDuration() {
        return duration;
    }
    public boolean isCompleted() {
        return completed;
    }
    public List<String> getThreadNames() {
        return threadNames;
    }
    public String getUsername() {
        return username;
    }
    public List<Span> getSpans() {
        return spans;
    }
    public MergedStackTreeNode getMergedStackTree() {
        return mergedStackTree;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("id", id)
                .add("from", from)
                .add("to", to)
                .add("stuck", stuck)
                .add("duration", duration)
                .add("completed", completed)
                .add("username", username)
                .add("spans", spans)
                .add("mergedStackTree", mergedStackTree)
                .toString();
    }

    public static class Span {

        private long offset;
        private long duration;
        private int index;
        private int parentIndex;
        private int level;
        private String description;
        private Map<String, Object> contextMap;

        public long getOffset() {
            return offset;
        }
        public long getDuration() {
            return duration;
        }
        public int getIndex() {
            return index;
        }
        public int getParentIndex() {
            return parentIndex;
        }
        public int getLevel() {
            return level;
        }
        public String getDescription() {
            return description;
        }
        public Map<String, Object> getContextMap() {
            return contextMap;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("offset", offset)
                    .add("duration", duration)
                    .add("index", index)
                    .add("parentIndex", parentIndex)
                    .add("level", level)
                    .add("description", description)
                    .add("contextMap", contextMap)
                    .toString();
        }
    }

    public static class MergedStackTreeNode {

        private String stackTraceElement;
        private List<MergedStackTreeNode> childNodes;
        private int sampleCount;
        private volatile Map<String, Integer> leafThreadStateSampleCounts;
        private volatile String singleLeafState;

        public String getStackTraceElement() {
            return stackTraceElement;
        }
        public List<MergedStackTreeNode> getChildNodes() {
            return childNodes;
        }
        public int getSampleCount() {
            return sampleCount;
        }
        public Map<String, Integer> getLeafThreadStateSampleCounts() {
            return leafThreadStateSampleCounts;
        }
        public String getSingleLeafState() {
            return singleLeafState;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("stackTraceElement", stackTraceElement)
                    .add("childNodes", childNodes)
                    .add("sampleCount", sampleCount)
                    .add("leafThreadStateSampleCounts", leafThreadStateSampleCounts)
                    .add("singleLeafState", singleLeafState)
                    .toString();
        }
    }
}