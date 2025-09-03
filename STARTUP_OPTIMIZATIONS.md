# Spring Boot Startup Optimizations

This document describes the startup performance optimizations implemented in Airsonic Advanced.

## Optimizations Applied

### 1. Lazy Initialization (`spring.main.lazy-initialization=true`)
- Beans are only created when first accessed, not during startup
- Reduces initial memory usage and startup time significantly
- Critical components (security, data sources) are handled separately via `StartupOptimizationConfig`

### 2. Component Index (`META-INF/spring.components`)
- Pre-computed index of Spring components to avoid expensive classpath scanning
- Eliminates the need to scan 194+ annotated classes during startup
- Automatically maintained list of configuration classes

### 3. Auto-Configuration Exclusions
Excluded unnecessary Spring Boot auto-configurations:
- `MailSenderAutoConfiguration` - Email functionality (loaded on-demand)
- `WebSocketServletAutoConfiguration` - WebSocket support (loaded lazily)
- `RepositoryRestMvcAutoConfiguration` - REST repositories (not used)
- `FreeMarkerAutoConfiguration` - FreeMarker templates (not used)
- `GroovyTemplateAutoConfiguration` - Groovy templates (not used)
- `MustacheAutoConfiguration` - Mustache templates (not used)

### 4. JPA/Hibernate Optimizations
- `spring.jpa.defer-datasource-initialization=true` - Defer database setup
- `hibernate.boot.allow_jdbc_metadata_access=false` - Skip metadata access during boot
- Optimized batch processing and connection pooling settings

### 5. Thread Pool Lazy Loading
- Thread pools (`BroadcastThreadPool`, `PodcastDownloadThreadPool`, etc.) are now lazy
- Pools are only created when podcast or broadcast functionality is actually used
- Removed eager `initialize()` calls

### 6. Caching Optimizations
- Cache configuration marked as `@Lazy`
- Thymeleaf template compilation enabled for production
- Static resource caching optimized

### 7. Development vs Production
- Development profile (`local-dev`) disables some optimizations for better debugging
- Production profile maintains all optimizations for fastest startup

## Expected Performance Improvements

- **Startup Time**: 30-50% reduction in cold start time
- **Memory Usage**: 20-30% less memory usage at startup
- **Bean Creation**: Only ~20-30 beans created at startup vs 194+ previously
- **First Request**: May be slightly slower as lazy beans are initialized on-demand

## Monitoring Startup Performance

Enable debug logging to monitor optimization effects:
```properties
logging.level.org.airsonic.player.config.StartupOptimizationConfig=DEBUG
```

## Rollback Strategy

To disable optimizations if issues arise:
```properties
spring.main.lazy-initialization=false
```

Or use the development profile:
```bash
java -jar airsonic.war --spring.profiles.active=local-dev
```

## Testing

Run `StartupOptimizationTest` to validate optimizations work correctly:
```bash
mvn test -Dtest=StartupOptimizationTest
```