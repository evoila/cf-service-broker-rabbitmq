package de.evoila.cf.cpi.openstack.custom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import de.evoila.cf.broker.persistence.repository.CrudRepositoryImpl;

/**
 * @author Christian Brinker, evoila.
 *
 */
@Repository
public class StackMappingRepository extends CrudRepositoryImpl<StackMapping, String> {

	@Autowired
	@Qualifier("jacksonStackMappingRedisTemplate")
	private RedisTemplate<String, StackMapping> redisTemplate;

	@Override
	protected RedisTemplate<String, StackMapping> getRedisTemplate() {
		return this.redisTemplate;
	}

	private static final String PREFIX = "stackmapping-";

	@Override
	protected String getPrefix() {
		return PREFIX;
	}

}
