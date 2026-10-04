package dev.booking.sports.shared.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

	@Bean
	public RedisCacheManager cacheManager(
			RedisConnectionFactory connectionFactory,
			@Value("${app.cache.user-details-ttl:5m}") Duration userDetailsTtl) {

		RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(userDetailsTtl)
				.disableCachingNullValues()
				.serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
				.serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.json()));

		return RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(configuration)
				.build();
	}

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setDefaultSerializer(jsonSerializer);

        return template;
    }
}