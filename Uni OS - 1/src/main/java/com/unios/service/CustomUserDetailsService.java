package com.unios.service;

import com.unios.model.User;
import com.unios.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println(">>> [CustomUserDetailsService] Loading user: " + email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    System.err.println("❌ [CustomUserDetailsService] User NOT FOUND: " + email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        System.out.println(">>> [CustomUserDetailsService] User found: " + user.getEmail() + " | Role: " + user.getRole());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
