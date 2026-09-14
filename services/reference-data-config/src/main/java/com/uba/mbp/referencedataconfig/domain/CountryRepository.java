package com.uba.mbp.referencedataconfig.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CountryRepository extends JpaRepository<Country, UUID> {

    Optional<Country> findByCountryCodeAndEffectiveToIsNull(String countryCode);

    List<Country> findAllByEffectiveToIsNull();

    boolean existsByCountryCodeAndEffectiveToIsNull(String countryCode);
}
