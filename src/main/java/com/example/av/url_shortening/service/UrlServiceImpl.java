package com.example.av.url_shortening.service;

import com.example.av.url_shortening.model.Url;
import com.example.av.url_shortening.model.UrlDto;
import com.example.av.url_shortening.repo.UrlRepo;
import com.google.common.hash.Hashing;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class UrlServiceImpl implements UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlServiceImpl.class);
    private static final String CACHE_PREFIX = "url:";

    @Autowired
    private UrlRepo urlRepo;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Url generateShortLink(UrlDto urlDto) {
        if (StringUtils.isNotEmpty(urlDto.getUrl())) {
            String encodedUrl = encodeUrl(urlDto.getUrl());
            Url urlToPersist = new Url();
            urlToPersist.setCreationDate(LocalDateTime.now());
            urlToPersist.setOriginalUrl(urlDto.getUrl());
            urlToPersist.setShortLink(encodedUrl);
            urlToPersist.setExpirationDate(
                    getExpirationDate(urlDto.getExpirationDate(), urlToPersist.getCreationDate()));
            Url savedUrl = persistShortLink(urlToPersist);
            if (savedUrl != null) {
                // Cache immediately after creation
                cacheUrl(savedUrl);
            }
            return savedUrl;
        }
        return null;
    }

    private LocalDateTime getExpirationDate(String expirationDate, LocalDateTime creationDate) {
        if (StringUtils.isBlank(expirationDate)) {
            return creationDate.plusSeconds(60);
        }
        return LocalDateTime.parse(expirationDate);
    }

    private String encodeUrl(String url) {
        LocalDateTime time = LocalDateTime.now();
        return Hashing.murmur3_32()
                .hashString(url.concat(time.toString()), StandardCharsets.UTF_8)
                .toString();
    }

    @Override
    public Url persistShortLink(Url url) {
        return urlRepo.save(url);
    }

    /**
     * Lookup short link — checks Redis first, falls back to DB on cache miss.
     */
    @Override
    public Url getEncodedUrl(String shortLink) {
        String cacheKey = CACHE_PREFIX + shortLink;

        // 1. Try Redis cache
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof Url cachedUrl) {
                log.debug("Cache HIT for shortLink: {}", shortLink);
                return cachedUrl;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to DB: {}", e.getMessage());
        }

        // 2. Cache miss — load from DB
        log.debug("Cache MISS for shortLink: {}", shortLink);
        Url url = urlRepo.findByShortLink(shortLink);

        // 3. Populate cache for future requests
        if (url != null) {
            cacheUrl(url);
        }
        return url;
    }

    /**
     * Evict from Redis cache when the URL is deleted.
     */
    @Override
    public void deleteShortLink(Url url) {
        try {
            redisTemplate.delete(CACHE_PREFIX + url.getShortLink());
            log.debug("Evicted cache for shortLink: {}", url.getShortLink());
        } catch (Exception e) {
            log.warn("Could not evict from Redis: {}", e.getMessage());
        }
        urlRepo.delete(url);
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private void cacheUrl(Url url) {
        try {
            String cacheKey = CACHE_PREFIX + url.getShortLink();
            long ttlSeconds = 60; // default TTL
            if (url.getExpirationDate() != null) {
                long secondsUntilExpiry = java.time.Duration
                        .between(LocalDateTime.now(), url.getExpirationDate())
                        .getSeconds();
                ttlSeconds = Math.max(1, secondsUntilExpiry);
            }
            redisTemplate.opsForValue().set(cacheKey, url, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Cached shortLink: {} with TTL {}s", url.getShortLink(), ttlSeconds);
        } catch (Exception e) {
            log.warn("Could not write to Redis cache: {}", e.getMessage());
        }
    }
}
