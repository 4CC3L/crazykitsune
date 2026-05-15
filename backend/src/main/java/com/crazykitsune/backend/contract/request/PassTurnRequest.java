package com.crazykitsune.backend.contract.request;

import jakarta.validation.constraints.NotBlank;

public record PassTurnRequest(@NotBlank String playerId) {
}