package com.example.claims.config;

import com.example.claims.domain.ClaimsStaff;
import com.example.claims.repository.ClaimsStaffRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner seedStaff(ClaimsStaffRepository staff) {
        return args -> {
            if (staff.count() == 0) {
                staff.save(new ClaimsStaff("Asha Rao", "asha.rao@example.com"));
                staff.save(new ClaimsStaff("Rahul Mehta", "rahul.mehta@example.com"));
            }
        };
    }
}
