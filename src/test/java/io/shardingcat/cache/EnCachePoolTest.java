
package io.shardingcat.cache;

import junit.framework.Assert;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.MemoryUnit;

import org.junit.Test;

import io.shardingcat.cache.impl.EnchachePool;

public class EnCachePoolTest {

	private static EnchachePool enCachePool;
	static {
		CacheConfiguration cacheConf = new CacheConfiguration();
		cacheConf.setName("testcache");
		cacheConf.maxBytesLocalHeap(50,MemoryUnit.MEGABYTES).timeToIdleSeconds(2);
		Cache cache=new Cache(cacheConf);
		CacheManager.create().addCache(cache);
		enCachePool = new EnchachePool(cacheConf.getName(),cache,50*10000);
	}

	@Test
	public void testBasic() {
		enCachePool.putIfAbsent("2", "dn2");
		enCachePool.putIfAbsent("1", "dn1");

		Assert.assertEquals("dn2", enCachePool.get("2"));
		Assert.assertEquals("dn1", enCachePool.get("1"));
		Assert.assertEquals(null, enCachePool.get("3"));

		CacheStatic statics = enCachePool.getCacheStatic();
		Assert.assertEquals(statics.getItemSize(), 2);
		Assert.assertEquals(statics.getPutTimes(), 2);
		Assert.assertEquals(statics.getAccessTimes(), 3);
		Assert.assertEquals(statics.getHitTimes(), 2);
		Assert.assertTrue(statics.getLastAccesTime() > 0);
		Assert.assertTrue(statics.getLastPutTime() > 0);
		Assert.assertTrue(statics.getLastAccesTime() > 0);
		// wait expire
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
		}
		Assert.assertEquals(null, enCachePool.get("2"));
		Assert.assertEquals(null, enCachePool.get("1"));
	}

}