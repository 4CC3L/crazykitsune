package com.crazykitsune.backend.contract.response;

import java.util.List;

public record RulesView(String gameName, List<String> basics, List<String> poweredCards, List<String> implementationNotes) {
}