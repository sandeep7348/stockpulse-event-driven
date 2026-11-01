package com.stockalert.alert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertRepository extends MongoRepository<Alert, String> {

	Page<Alert> findByUserId(String userId, Pageable pageable);

	Page<Alert> findByUserIdAndStatusIn(String userId, Collection<AlertStatus> statuses, Pageable pageable);

	Optional<Alert> findByIdAndUserId(String id, String userId);

	long countByUserIdAndStatus(String userId, AlertStatus status);

	List<Alert> findByStatus(AlertStatus status);

}
