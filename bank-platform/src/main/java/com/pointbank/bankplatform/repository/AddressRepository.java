package com.pointbank.bankplatform.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pointbank.bankplatform.entity.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findAllByCustomer_Id(UUID customerId);
}
