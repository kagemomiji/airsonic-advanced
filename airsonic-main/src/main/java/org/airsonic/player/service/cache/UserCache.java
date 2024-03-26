package org.airsonic.player.service.cache;

import org.airsonic.player.domain.User;
import org.airsonic.player.spring.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import javax.cache.CacheManager;

@Component
public class UserCache {

    @Autowired
    private CacheManager cacheManager;

    public void clear() {
        cacheManager.getCache(CacheConfiguration.USER_CACHE).clear();
    }

    public User getUser(@Nullable String username) {
        if (username == null) {
            return null;
        }
        return cacheManager.getCache(CacheConfiguration.USER_CACHE, String.class, User.class).get(username);
    }

    public void putUser(@Nullable String username, @Nullable User user) {
        if (username == null || user == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.USER_CACHE, String.class, User.class).put(username, user);
    }

    public void removeUser(@Nullable String username) {
        if (username == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.USER_CACHE, String.class, User.class).remove(username);
    }

}