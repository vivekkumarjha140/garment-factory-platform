package com.garmentfactory.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.customer.service.IndianStates;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class IndianStatesTest {

    @Test
    void listsAll28StatesAnd8UnionTerritoriesWithoutDuplicates() {
        assertThat(IndianStates.all()).hasSize(36);
        assertThat(new HashSet<>(IndianStates.all())).hasSize(36);
    }

    @Test
    void returnsTheOfficialSpellingForAnyCaseAndSpacing() {
        assertThat(IndianStates.canonical(" rajasthan ")).contains("Rajasthan");
        assertThat(IndianStates.canonical("TAMIL NADU")).contains("Tamil Nadu");
        assertThat(IndianStates.canonical("dadra and nagar haveli and daman and diu"))
                .contains("Dadra and Nagar Haveli and Daman and Diu");
    }

    @Test
    void rejectsUnknownOrMissingStates() {
        assertThat(IndianStates.canonical("Atlantis")).isEmpty();
        assertThat(IndianStates.canonical("")).isEmpty();
        assertThat(IndianStates.canonical(null)).isEmpty();
    }
}