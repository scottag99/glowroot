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
package org.glowroot.api.internal;

import java.util.Map;

import checkers.igj.quals.ReadOnly;

/**
 * This interface exists to provide access to MessageImpl from glowroot without making MessageImpl
 * accessible to plugins (at least not through the org.glowroot.api package)
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
public interface ReadableMessage {

    String getText();

    @ReadOnly
    Map<String, ? extends /*@Nullable*/Object> getDetail();
}
