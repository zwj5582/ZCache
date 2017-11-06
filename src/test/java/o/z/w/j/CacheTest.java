/*
 * Created by ZhongWenjie on 2017-11-03 15:38.
 */

package o.z.w.j;

import o.z.w.j.cache.CacheLoader;
import o.z.w.j.cache.LocalCache;
import o.z.w.j.cache.Strength;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class CacheTest {

	@Test
	public void test() {
		LocalCache<Integer,Integer> cache = new LocalCache<Integer, Integer>(new CacheLoader<Integer,Integer>(){
			@Override
			public Integer load(Integer key) {
				System.out.println("===   " + key + "   ===");
				return key*key;
			}
		},16,10 ,TimeUnit.SECONDS, Strength.STRONG);

		System.out.println(cache.get(17));
		System.out.println(cache.put(25,13));
		try {
			Thread.sleep(1000*5);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println(cache.get(17));
		System.out.println(cache.get(25));
		try {
			Thread.sleep(1000*12);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println(cache.get(17));
		System.out.println(cache.get(25));
	}

}
