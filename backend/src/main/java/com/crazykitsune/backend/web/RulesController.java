package com.crazykitsune.backend.web;

import com.crazykitsune.backend.generated.api.RulesApi;
import com.crazykitsune.backend.generated.model.RulesView;
import com.crazykitsune.backend.service.RulesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class RulesController implements RulesApi {

    private final RulesService rulesService;

    public RulesController(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    @Override
    public ResponseEntity<RulesView> getRules() {
        return ResponseEntity.ok(rulesService.getRules());
    }
}