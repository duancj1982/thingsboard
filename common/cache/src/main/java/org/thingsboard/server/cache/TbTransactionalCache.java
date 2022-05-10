/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.cache;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TbTransactionalCache<K extends Serializable, V extends Serializable> {

    String getCacheName();

    TbCacheValueWrapper<V> get(K key);

    void putIfAbsent(K key, V value);

    void evict(K key);

    void evict(Collection<K> keys);

    void evictOrPut(K key, V value);

    TbCacheTransaction<K, V> newTransactionForKey(K key);

    TbCacheTransaction<K, V> newTransactionForKeys(List<K> keys);

    default V getAndPutInTransaction(K key, Supplier<V> dbCall, boolean cacheNullValue) {
        TbCacheValueWrapper<V> cacheValueWrapper = get(key);
        if (cacheValueWrapper != null) {
            return cacheValueWrapper.get();
        }
        var cacheTransaction = newTransactionForKey(key);
        try {
            V dbValue = dbCall.get();
            if (dbValue != null || cacheNullValue) {
                cacheTransaction.putIfAbsent(key, dbValue);
                cacheTransaction.commit();
                return dbValue;
            } else {
                cacheTransaction.rollback();
                return null;
            }
        } catch (Throwable e) {
            cacheTransaction.rollback();
            throw e;
        }
    }

    default <R> R getAndPutInTransaction(K key, Supplier<R> dbCall, Function<V, R> cacheValueToResult, Function<R, V> dbValueToCacheValue, boolean cacheNullValue) {
        TbCacheValueWrapper<V> cacheValueWrapper = get(key);
        if (cacheValueWrapper != null) {
            var cacheValue = cacheValueWrapper.get();
            return cacheValue == null ? null : cacheValueToResult.apply(cacheValue);
        }
        var cacheTransaction = newTransactionForKey(key);
        try {
            R dbValue = dbCall.get();
            if (dbValue != null || cacheNullValue) {
                cacheTransaction.putIfAbsent(key, dbValueToCacheValue.apply(dbValue));
                cacheTransaction.commit();
                return dbValue;
            } else {
                cacheTransaction.rollback();
                return null;
            }
        } catch (Throwable e) {
            cacheTransaction.rollback();
            throw e;
        }
    }

}