/*
 * MIT License
 *
 * Copyright (c) 2020-present Cloudogu GmbH and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package sonia.scm.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.synchronizedMap;

class StoreCache <S> {

  private static final Logger LOG = LoggerFactory.getLogger(StoreCache.class);

  public static final String ENABLE_STORE_CACHE_PROPERTY = "scm.storeCache.enabled";

  private final Function<TypedStoreParameters<?>, S> cachingStoreCreator;

  StoreCache(Function<TypedStoreParameters<?>, S> storeCreator) {
    if (Boolean.getBoolean(ENABLE_STORE_CACHE_PROPERTY)) {
      LOG.info("store cache enabled");
      Map<TypedStoreParameters<?>, S> storeCache = synchronizedMap(new HashMap<>());
      cachingStoreCreator = storeParameters -> storeCache.computeIfAbsent(storeParameters, storeCreator);
    } else {
      cachingStoreCreator = storeCreator;
    }
  }

  S getStore(TypedStoreParameters<?> storeParameters) {
    return cachingStoreCreator.apply(storeParameters);
  }
}
