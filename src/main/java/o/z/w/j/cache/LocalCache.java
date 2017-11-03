/*
 * Created by ZhongWenjie on 2017-11-03 10:34.
 */

package o.z.w.j.cache;

import java.util.AbstractMap;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

public class LocalCache<K,V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {

	final Segment<K, V>[] segments;                 // 段数组
	final int segmentMask;                          // 段掩码
	final int segmentShift;                         // 段偏移

	long expireTime;

	TimeUnit unit;

	final int concurrencyLevel;                     // 最大并发量

	final CacheLoader<? super K, V> defaultLoader;  // loader

	public LocalCache(CacheLoader<K, V> loader, int concurrencyLevel, long expireTime, TimeUnit timeUnit){
		this.expireTime = timeUnit.toMillis(expireTime);
		this.unit = timeUnit;

		this.defaultLoader = loader;

		int v = 1;
		for ( ; v < Math.min(concurrencyLevel, 16) ;  v = v << 1 );
		this.concurrencyLevel = Math.min(v, 16);

		int s = 1;
		int shift = 0;
		for ( ;s < concurrencyLevel; s = s << 1 ,shift++ );
		this.segmentShift = 32 - shift ;
		this.segmentMask = concurrencyLevel - 1;

		int segmentSize = concurrencyLevel;
		this.segments = newSegments(segmentSize);

		for (int i = 0; i < segments.length ; i++ ){
			segments[i] = createSegment(this,concurrencyLevel);
		}
	}

	public V get(Object key){
		return getOrLoad((K)key,this.defaultLoader);
	}

	V getOrLoad(K key, CacheLoader loader){
		int hash = key.hashCode();
		return segmentFor(hash).get(key,hash,loader);
	}

	public V put( K key ,V value) {
		int hash = key.hashCode();
		return segmentFor(hash).put(key, hash, value);
	}

	private Segment<K,V> segmentFor(int hash) {
		return segments[( hash >> segmentShift ) & segmentMask ];
	}

	private Segment<K,V> createSegment(LocalCache<K, V> cache,int concurrencyLevel) {
		return new Segment<>(cache,concurrencyLevel);
	}

	private Segment<K,V>[] newSegments(int size){
		return new Segment[size];
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return null;
	}

	@Override
	public V putIfAbsent(K key, V value) {
		return null;
	}

	@Override
	public boolean remove(Object key, Object value) {
		return false;
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		return false;
	}

	@Override
	public V replace(K key, V value) {
		return null;
	}

	static class Segment<K,V> extends ReentrantLock{

		volatile AtomicReferenceArray<ReferenceEntry<K, V>> table;

		final LocalCache<K, V> map;

		Segment(LocalCache<K,V> map,int size){
			this.map = map;
			table = newEntryArray(size);
		}

		private AtomicReferenceArray<ReferenceEntry<K,V>> newEntryArray(int size) {
			return new AtomicReferenceArray<ReferenceEntry<K, V>>(size);
		}

		public V put(K key, int hash, V value) {
			lock();
			try {
				preWriteCleanup(new Date().getTime());
				AtomicReferenceArray<ReferenceEntry<K, V>> referenceArray = this.table;
				int index = hash & (table.length() - 1);
				ReferenceEntry<K, V> first = referenceArray.get(index);
				ReferenceEntry<K,V> preEntry = null;
				for (ReferenceEntry<K,V> entry = first; entry !=null; entry=entry.next() ){
					preEntry = entry;
					K e = entry.getKey();
					int hashCode = e.hashCode();
					if (hashCode == hash){
						V oldValue = entry.getValue();
						setValue(entry,value,new Date().getTime());
						return oldValue;
					}
				}
				ReferenceEntry<K,V> newEntry = newEntry(key,hash,first);
				addNewEntry(newEntry,value,new Date().getTime(),preEntry,index);
				return null;
			}finally {
				unlock();
			}
		}

		private void addNewEntry(ReferenceEntry<K, V> newEntry, V value, long time, ReferenceEntry<K, V> preEntry,int index) {
			newEntry.setValue(value);
			newEntry.setTime(time);
			if (preEntry!=null)
				preEntry.setNext(newEntry);
			else{
				this.table.set(index,newEntry);
			}
		}

		private void preWriteCleanup(long time) {
			AtomicReferenceArray<ReferenceEntry<K, V>> t = this.table;
			if (table.length() > 0){
				for (int i = 0; i < table.length(); i++){
					ReferenceEntry<K, V> entry = table.get(i);
					ReferenceEntry<K,V> preEntry = null;
					for (ReferenceEntry<K,V> e = entry; e !=null; e=e.next() ){
						if (e.getTime() + map.expireTime < time){
							if (preEntry==null){
								table.set(i,null);
								break;
							}else{
								preEntry.setNext(e.next());
								e = preEntry;
							}
						}
						preEntry=e;
					}
				}
			}
		}

		private void setValue(ReferenceEntry<K, V> newEntry, V value, long time) {
			newEntry.setValue(value);
			newEntry.setTime(time);
		}

		private ReferenceEntry<K,V> newEntry(K key, int hash, ReferenceEntry<K,V> next){
			return new StrongEntry<>(key,hash,next);
		}

		public V get(K key, int hash, CacheLoader<K,V> loader) {
			preWriteCleanup(new Date().getTime());
			ReferenceEntry<K,V> entry = getEntry(key,hash);
			if (entry != null){
				return entry.getValue();
			}else{
				V value = loader.load(key);
				put(key,hash,value);
				return value;
			}
		}

		private void addNewEntry(ReferenceEntry<K, V> entry) {
			int hash = entry.getKey().hashCode();
			ReferenceEntry<K, V> preEntry = getFirst(hash);
			for (; preEntry!=null && preEntry.next() != null; preEntry = preEntry.next() );
			if (preEntry==null){
				table.set(hash & (table.length() - 1),entry);
			}else{
				preEntry.setNext(entry);
			}
		}

		private ReferenceEntry<K,V> getEntry(K key, int hash) {
			ReferenceEntry<K, V> first = getFirst(hash);
			for (ReferenceEntry<K,V> entry = first; entry != null; entry=entry.next()){
				K k = entry.getKey();
				if (k == key)
					return entry;
			}
			return null;
		}

		private ReferenceEntry<K,V> getFirst(int hash) {
			AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
			return table.get(hash & (table.length() - 1));
		}
	}


}
