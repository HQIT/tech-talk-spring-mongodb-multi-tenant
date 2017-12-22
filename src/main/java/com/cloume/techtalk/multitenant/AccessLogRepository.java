package com.cloume.techtalk.multitenant;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccessLogRepository extends MongoRepository<MultiTenantApplication.AccessLog, String> {
}
