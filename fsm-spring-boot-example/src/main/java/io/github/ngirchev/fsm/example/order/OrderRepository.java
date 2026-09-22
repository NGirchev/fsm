package io.github.ngirchev.fsm.example.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // The event handler locks the order until its state change commits.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from PurchaseOrder orders where orders.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
