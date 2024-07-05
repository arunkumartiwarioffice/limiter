package com.rate.limiter.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@RestController
public class RateLimitController {
	
	@Autowired
	private StringRedisTemplate redisTemplate;

	private static final int RATE_LIMIT = 100; // max requests
	private static final int WINDOW_SIZE_IN_MINUTES = 1; // time window

	@SuppressWarnings("deprecation")
	@GetMapping("/api")
	public String handleRequest(@RequestHeader("Client-Id") String clientId) {
		String key = "rate_limiter:" + clientId;
		ValueOperations<String, String> ops = redisTemplate.opsForValue();

		long currentTime = Instant.now().getEpochSecond();
		long windowStart = currentTime - (WINDOW_SIZE_IN_MINUTES * 60);

		// Cleanup old requests
		redisTemplate.execute((connection) -> {
			connection.zRemRangeByScore(key.getBytes(), 0, windowStart);
			return null;
		});

		Long requestCount = redisTemplate.opsForZSet().zCard(key);
		if (requestCount != null && requestCount >= RATE_LIMIT) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Try again later.");
		}

		redisTemplate.opsForZSet().add(key, String.valueOf(currentTime), currentTime);
		redisTemplate.expire(key, WINDOW_SIZE_IN_MINUTES, TimeUnit.MINUTES);

		return "Request successful";
	}
}
