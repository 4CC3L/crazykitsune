package com.crazykitsune.backend.contract.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PlayCardsRequest(
    @NotBlank String playerId,
    @NotEmpty List<String> cardIds,
    String declaredSuit,
    String targetPlayerId
) {
}