/*
 * Copyright (c), Data Geekery GmbH, contact@datageekery.com
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
package com.fasterxml.jackson.module.blackbird.util;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

public class Unchecked {
    private Unchecked() {
        throw new RuntimeException();
    }

    @SuppressWarnings("PMD.DoNotUseThreads")
    public static Runnable runnable(CheckedRunnable checkedRunnable) {
        return () -> {
            try {
                checkedRunnable.run();
            } catch (Throwable t) {
                throw Sneaky.throwAnyway(t);
            }
        };
    }

    public static <T> Supplier<T> supplier(CheckedSupplier<T> checkedSupplier) {
        return () -> {
            try {
                return checkedSupplier.get();
            } catch (Throwable t) {
                throw Sneaky.throwAnyway(t);
            }
        };
    }

    public static <X, T> Function<X, T> function(CheckedFunction<X, T> checkedFunction) {
        return x -> {
            try {
                return checkedFunction.apply(x);
            } catch (Throwable t) {
                throw Sneaky.throwAnyway(t);
            }
        };
    }

    public interface SneakyCallable<T> extends Callable<T> {
        @Override
        T call(); // no 'throws Exception'
    }

    public interface CheckedRunnable {
        void run() throws Throwable;
    }
}
