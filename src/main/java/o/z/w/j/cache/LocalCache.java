/*
 * Created by ZhongWenjie on 2017-11-03 10:34.
 */

package o.z.w.j.cache;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
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

	final Strength strength;

	public LocalCache(CacheLoader<K, V> loader, int concurrencyLevel, long expireTime, TimeUnit timeUnit,Strength strength){
		this.expireTime = timeUnit.toMillis(expireTime);
		this.unit = timeUnit;
		this.strength = strength;

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

	Set<K> keySet;

	@Override
	public Set<K> keySet() {
		Set<K> ks = keySet;
		return (ks != null) ? ks : (keySet = new KeySet(this));
	}

	@Override
	public V putIfAbsent(K key, V value) {
		return null;
	}

	@Override
	public V remove(Object key) {
		if (key==null)return null;
		int hash = key.hashCode();
		return segmentFor(hash).remove(key,hash);
	}

	Collection<V> values;

	@Override
	public Collection<V> values() {
		Collection<V> vs = values;
		return (vs != null) ? vs : (values = new Values(this));
	}

	@Override
	public boolean remove(Object key, Object value) {
		if (key == null) return false;
		int hash = key.hashCode();
		return segmentFor(hash).remove(key,hash,(V)value);
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

		final Queue<ReferenceEntry<K,V>> accessQueue;

		final ReferenceQueue<V> valueReferenceQueue;

		Segment(LocalCache<K,V> map,int size){
			this.map = map;
			this.accessQueue = new ConcurrentLinkedQueue<>();
			valueReferenceQueue = map.strength.equals(Strength.STRONG) ? null : new ReferenceQueue<V>();
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
				for (ReferenceEntry<K,V> entry = first; entry !=null; entry=entry.next() ){
					K e = entry.getKey();
					int hashCode = e.hashCode();
					if (hashCode == hash){
						ValueReference<K,V> oldValue = entry.getValue();
						setValue(entry,oldValue,new Date().getTime());
						return oldValue.get();
					}
				}
				ReferenceEntry<K,V> newEntry = newEntry(key,hash,first);
				addNewEntry(newEntry,value,new Date().getTime(),first,index);
				return null;
			}finally {
				unlock();
			}
		}

		private void addNewEntry(ReferenceEntry<K, V> newEntry, V value, long time, ReferenceEntry<K, V> first,int index) {
			ValueReference<K,V> v = ( this.valueReferenceQueue == null ) ?
					new StrongValueReference<K, V>(value) :
					new SoftValueReference<K, V>(value, newEntry) ;
			newEntry.setValue(v);
			newEntry.setTime(time);
			newEntry.setNext(first);
			this.table.set(index,newEntry);
			accessQueue.add(newEntry);
		}

		private void preWriteCleanup(long time) {
			ReferenceEntry<K,V> peek = null;
			while( (peek = accessQueue.peek()) !=null && ( peek.getTime() + map.expireTime < time ) ){
				removeFromArray(peek);
				accessQueue.remove(peek);
			}
		}

		private void removeFromArray(ReferenceEntry<K, V> peek) {
			K key = peek.getKey();
			int hash = key.hashCode();
			AtomicReferenceArray<ReferenceEntry<K, V>> array = this.table;
			int index = hash & (table.length() - 1);
			ReferenceEntry<K, V> first = array.get(index);
			ReferenceEntry<K,V> next = peek.next();
			for (ReferenceEntry<K,V> entry = first; entry != peek && entry!=null; entry = entry.next()){
				ReferenceEntry<K, V> newEntry = copyFor(entry,next);
				if (newEntry != null)
					next = newEntry;
				else
					this.accessQueue.remove(entry);
			}
			array.set(index,next);
		}

		private ReferenceEntry<K,V> copyFor(ReferenceEntry<K, V> entry, ReferenceEntry<K, V> next) {
			if (entry==null)return null;
			ValueReference<K, V> entryValue = entry.getValue();
			if (entryValue == null)return null;
			V value = entryValue.get();
			if (value == null)return null;
			K entryKey = entry.getKey();
			int hashCode = entryKey.hashCode();
			ReferenceEntry<K, V> newEntry = newEntry(entryKey, hashCode, next);
			newEntry.setValue(entryValue.copyFor(this.valueReferenceQueue,value,entry));
			newEntry.setTime(new Date().getTime());
			return newEntry;
		}

		private void setValue(ReferenceEntry<K, V> newEntry, ValueReference<K,V> value, long time) {
			newEntry.setValue(value);
			newEntry.setTime(time);
			accessQueue.add(newEntry);
		}

		private ReferenceEntry<K,V> newEntry(K key, int hash, ReferenceEntry<K,V> next){
			return new StrongEntry<>(key,hash,next);
		}

		public V get(K key, int hash, CacheLoader<K,V> loader) {
			long time = new Date().getTime();
			preWriteCleanup(time);
			ReferenceEntry<K,V> entry = null;
			lock();
			try {
				entry = getEntry(key,hash);
				if (entry != null  && ( (entry.getTime() + this.map.expireTime) >= time ) ){
					accessQueue.add(entry);
					return (entry.getValue() == null) ? null : entry.getValue().get() ;
				}else{
					int index = hash & (table.length() - 1);
					entry = ( entry != null && accessQueue.remove(entry) ) ?
							entry : newEntry(key, hash, table.get(index));
					entry.setValue(new LoadingValueReference<K, V>(loader,key));
					this.table.set(index,entry);
				}
			}finally {
				unlock();
			}
			return loadSync(entry);
		}

		private V loadSync(ReferenceEntry<K, V> entry) {
			ValueReference<K, V> value = entry.getValue();
			V result = value.get();
			entry.setTime(new Date().getTime());
			return result;
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

		public V remove(Object key, int hash) {
			V result = null;
			lock();
			try {
				long time = new Date().getTime();
				preWriteCleanup(time);
				ReferenceEntry<K, V> first = getFirst(hash);
				for (ReferenceEntry<K,V> entry = first; entry != null; entry = entry.next()){
					K k = entry.getKey();
					ValueReference<K, V> entryValue = entry.getValue();
					result = ( entryValue == null ) ? null : entryValue.get();
					if (k.hashCode() == hash && k.equals(key)){
						removeFromArray(entry);
						accessQueue.remove(entry);
						break;
					}
				}
			}finally {
				unlock();
			}
			return result;
		}

		public boolean remove(Object key, int hash, V value) {
			boolean result = false;
			lock();
			try {
				long time = new Date().getTime();
				preWriteCleanup(time);
				ReferenceEntry<K, V> first = getFirst(hash);
				for (ReferenceEntry<K,V> entry = first; entry != null; entry = entry.next()){
					K k = entry.getKey();
					ValueReference<K, V> entryValue = entry.getValue();
					if (k.hashCode() == hash && k.equals(key)){
						if (entryValue != null && value.equals(entryValue.get())){
							result = true;
							removeFromArray(entry);
							accessQueue.remove(entry);
						}
						break;
					}
				}
			}finally {
				unlock();
			}
			return result;
		}
	}


	abstract class HashIterator<T> implements Iterator<T> {

		int currentSegmentIndex ;
		int currentTableIndex;
		ReferenceEntry<K,V> currentEntry;

		@Override
		public boolean hasNext() {
			ReferenceEntry<K, V> entry = null;
			while ( currentSegmentIndex < segments.length ){
				Segment<K,V> segment = null;
				if ((segment = segments[currentSegmentIndex]) == null){
					currentSegmentIndex++;
					currentTableIndex = 0;
					continue;
				}
				AtomicReferenceArray<ReferenceEntry<K, V>> table = segment.table;
				if (table == null || table.length() <= 0 ){
					currentSegmentIndex++;
					currentTableIndex = 0;
					continue;
				}
				while (currentTableIndex < table.length()){
					entry = table.get(currentTableIndex);
					if(!getLiveEntry(table.get(currentTableIndex)))continue;
					break;
				}
				if (entry == null ){
					currentSegmentIndex++;
					currentTableIndex = 0;
					continue;
				}
				break;
			}
			if (entry == null)return false;
			currentEntry = entry;
			currentTableIndex++;
			return true;
		}

		private boolean getLiveEntry(ReferenceEntry<K, V> entry){
			if (entry == null){
				currentTableIndex++;return false;
			}
			if (entry.getTime() + expireTime < new Date().getTime()){
				currentTableIndex++;return false;
			}
			ValueReference<K, V> value = entry.getValue();
			if (value == null ){
				currentTableIndex++;return false;
			}
			V v = value.get();
			if ( v==null ){
				currentTableIndex++;return false;
			}
			return true;
		}

		public abstract T next();

		@Override
		public void remove() {

		}
	}

	final class KeySet extends AbstractSet<K>{

		ConcurrentMap<K,V> map;

		KeySet(ConcurrentMap<K,V> map){
			this.map = map;
		}

		@Override
		public Iterator<K> iterator() {
			return new KeyIterator();
		}

		@Override
		public int size() {
			return map.size();
		}
	}

	final class KeyIterator extends HashIterator<K>{

		@Override
		public K next() {
			return super.currentEntry.getKey();
		}

	}

	final class ValueIterator extends HashIterator<V>{

		@Override
		public V next() {
			return super.currentEntry.getValue().get();
		}

	}

	final class EntryIterator extends HashIterator<ReferenceEntry<K,V>>{

		@Override
		public ReferenceEntry<K, V> next() {
			return super.currentEntry;
		}

	}


	class Values extends AbstractSet<V> {

		final ConcurrentMap<K,V> map;

		public Values(ConcurrentMap<K,V> map) {
			this.map = map;
		}

		@Override
		public Iterator<V> iterator() {
			return new ValueIterator();
		}

		@Override
		public int size() {
			return map.size();
		}
	}
}
