/*
 * Copyright 2011-2013 the original author or authors.
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

import checkers.nullness.quals.Nullable;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
class LevelTwo {

    @Nullable
    private final RuntimeException exception;

    LevelTwo() {
        this(null);
    }

    LevelTwo(@Nullable RuntimeException e) {
        exception = e;
    }

    // this method corresponds to LevelTwoAspect
    void call(String arg1, String arg2) {
        new LevelThree(exception).call(arg1 + "y", arg2 + "y");
    }
}
