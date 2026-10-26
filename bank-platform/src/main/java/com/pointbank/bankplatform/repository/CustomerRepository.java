package com.pointbank.bankplatform.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pointbank.bankplatform.entity.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByCustomerId(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.customerId = :customerId")
    Optional<Customer> findByCustomerIdForUpdate(@Param("customerId") Long customerId);

    Optional<Customer> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where lower(c.email) = lower(:email)")
    Optional<Customer> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    Optional<Customer> findByIdentityIssuerAndIdentitySubject(String issuer, String subject);

    Optional<Customer> findByMobileNumber(String mobileNumber);

    boolean existsByCustomerId(Long customerId);

    @EntityGraph(attributePaths = "addresses")
    Optional<Customer> findWithAddressesById(UUID id);

    @EntityGraph(attributePaths = "addresses")
    Optional<Customer> findWithAddressesByMobileNumber(String mobileNumber);

    @EntityGraph(attributePaths = "addresses")
    Optional<Customer> findWithAddressesByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "addresses")
    Optional<Customer> findWithAddressesByCustomerId(Long customerId);

    @Query("select distinct c from Customer c left join fetch c.addresses where c.id in :ids")
    List<Customer> findAllWithAddressesByIdIn(@Param("ids") Collection<UUID> ids);
}
