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
package org.glowroot.sandbox.ui;

import java.util.Random;
import java.util.concurrent.Executors;

import checkers.igj.quals.Immutable;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Immutable
public class ExpensiveCall {

    private static final Random random = new Random();

    private final int maxTimeMillis;
    private final int maxSpanTextLength;

    ExpensiveCall(int maxTimeMillis, int maxSpanTextLength) {
        this.maxTimeMillis = maxTimeMillis;
        this.maxSpanTextLength = maxSpanTextLength;
    }

    void execute() {
        int route = random.nextInt(10);
        switch (route) {
            case 0:
                execute0();
                return;
            case 1:
                execute1();
                return;
            case 2:
                execute2();
                return;
            case 3:
                execute3();
                return;
            case 4:
                execute4();
                return;
            case 5:
                execute5();
                return;
            case 6:
                execute6();
                return;
            case 7:
                execute7();
                return;
            case 8:
                execute8();
                return;
            case 9:
                execute9();
                return;
        }
    }

    private void execute0() {
        expensive();
    }

    private void execute1() {
        expensive();
    }

    private void execute2() {
        expensive();
    }

    private void execute3() {
        expensive();
    }

    private void execute4() {
        expensive();
    }

    private void execute5() {
        expensive();
    }

    private void execute6() {
        expensive();
    }

    private void execute7() {
        expensive();
    }

    private void execute8() {
        expensive();
    }

    private void execute9() {
        expensive();
    }

    public String getSpanText() {
        return getSpanText(random.nextInt(5) > 0);
    }

    // this is just to prevent jvm from optimizing away for the loop below
    public static volatile long dummy;

    // need
    private static final Object lock = new Object();

    static {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // this loop is used to block threads executing expensive() below
                    while (true) {
                        synchronized (lock) {
                            Thread.sleep(random.nextInt(10));
                        }
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                }
            }
        });
    }

    private void expensive() {
        int millis = random.nextInt(maxTimeMillis) / 4;
        // spend a quarter of the time taxing the cpu and doing memory allocation
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < millis) {
            for (int i = 0; i < 100000; i++) {
                dummy += random.nextInt(1024);
                if (i % 100 == 0) {
                    dummy += new byte[random.nextInt(1024)].length;
                }
            }
        }
        // spend the rest of the time in both blocking and waiting states
        start = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - start < 3 * millis) {
                synchronized (lock) {
                    Thread.sleep(random.nextInt(10));
                    dummy++;
                }
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
        }
    }

    private String getSpanText(boolean spaces) {
        int spanTextLength = random.nextInt(maxSpanTextLength);
        StringBuilder sb = new StringBuilder(spanTextLength);
        for (int i = 0; i < spanTextLength; i++) {
            // random lowercase character
            sb.append((char) ('a' + random.nextInt(26)));
            if (spaces && random.nextInt(6) == 0) {
                // on average, one of six characters will be a space
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
