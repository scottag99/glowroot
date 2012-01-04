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
package org.informantproject.local.metric;

import com.google.common.base.Objects;

/**
 * Structure used as part of the response to "/metrics".
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
public class Point {

    private long capturedAt;
    private double value;

    public Point() {}

    public Point(long capturedAt, double value) {
        this.capturedAt = capturedAt;
        this.value = value;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(long capturedAt) {
        this.capturedAt = capturedAt;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) {
            return false;
        }
        Point other = (Point) o;
        return Objects.equal(capturedAt, other.getCapturedAt())
                && Objects.equal(value, other.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(capturedAt, value);
    }
}