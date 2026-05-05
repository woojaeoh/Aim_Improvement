package capstone25_2.aim;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        log.warn("Redis 캐시 조회 실패 (cache={}, key={}). DB에서 직접 조회합니다.", cache.getName(), key);
    }

    @Override
    public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        log.warn("Redis 캐시 저장 실패 (cache={}, key={})", cache.getName(), key);
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        log.warn("Redis 캐시 삭제 실패 (cache={}, key={})", cache.getName(), key);
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.warn("Redis 캐시 전체 삭제 실패 (cache={})", cache.getName());
    }
}
