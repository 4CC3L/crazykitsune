package com.crazykitsune.backend.contract.request;

import jakarta.validation.constraints.NotBlank;

public record DrawCardRequest(@NotBlank String playerId, boolean fromDiscard) {
}