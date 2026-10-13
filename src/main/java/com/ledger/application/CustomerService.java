package com.ledger.application;

import com.ledger.api.dto.request.CreateCustomerRequest;
import com.ledger.api.dto.response.CustomerResponse;
import com.ledger.domain.exception.DuplicateCustomerException;
import com.ledger.domain.exception.TreasuryProtectedException;
import com.ledger.domain.model.Customer;
import com.ledger.domain.model.CustomerStatus;
import com.ledger.domain.model.SystemParties;
import com.ledger.infrastructure.persistence.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse register(CreateCustomerRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (SystemParties.TREASURY_EMAIL.equals(email)) {
            throw new TreasuryProtectedException("The treasury principal cannot be registered through the API");
        }
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateCustomerException(email);
        }
        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setFullName(request.fullName().trim());
        customer.setStatus(request.status() == null ? CustomerStatus.ACTIVE : request.status());
        return CustomerResponse.from(customerRepository.save(customer));
    }
}
