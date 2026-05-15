package com.crazykitsune.backend.contract.request;

import jakarta.validation.constraints.NotBlank;

public record PlayerRequest(@NotBlank String playerName) {
}