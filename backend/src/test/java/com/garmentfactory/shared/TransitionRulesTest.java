package com.garmentfactory.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.statemachine.TransitionRules;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TransitionRulesTest {

    enum Light { RED, GREEN, YELLOW, BROKEN }

    private final TransitionRules<Light> rules = TransitionRules.forType(Light.class)
            .allow(Light.RED, Light.GREEN)
            .allow(Light.GREEN, Light.YELLOW)
            .allow(Light.YELLOW, Light.RED, Light.BROKEN)
            .build();

    @Test
    void allowsListedMovesOnly() {
        assertThat(rules.canMove(Light.RED, Light.GREEN)).isTrue();
        assertThat(rules.canMove(Light.YELLOW, Light.BROKEN)).isTrue();
        assertThat(rules.canMove(Light.RED, Light.YELLOW)).isFalse();
        assertThat(rules.canMove(Light.GREEN, Light.RED)).isFalse();
    }

    @Test
    void aStatusWithNoListedMovesIsTerminal() {
        assertThat(rules.canMove(Light.BROKEN, Light.RED)).isFalse();
    }

    @Test
    void requireThrowsAConflictForAnInvalidMove() {
        assertThatCode(() -> rules.require(Light.RED, Light.GREEN, "A light")).doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.require(Light.RED, Light.BROKEN, "A light"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("INVALID_TRANSITION");
                    assertThat(ex.getMessage()).contains("RED").contains("BROKEN");
                });
    }
}