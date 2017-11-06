/*
 * Created by ZhongWenjie on 2017-11-06 15:50.
 */

package o.z.w.j.cache;

import java.util.concurrent.TimeUnit;

public final class CacheBuilder<K,V> {

	private CacheLoader<? super K, V> defaultLoader;

	private long expireTime;

	private TimeUnit unit;

	private int concurrencyLevel;

	private static final int DEFAULT_CONCURRENCY_LEVEL = 4;

	private static final long DEFAULT_EXPIRATION_TIME = 0;

	private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

}
