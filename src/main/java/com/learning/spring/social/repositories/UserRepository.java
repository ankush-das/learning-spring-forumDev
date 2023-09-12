package com.learning.spring.social.repositories;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.learning.spring.social.entities.User;

public interface UserRepository extends CrudRepository<User, Integer> {
    public Optional<User> findByName(String name);
}
