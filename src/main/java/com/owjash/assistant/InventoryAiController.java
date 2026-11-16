package com.owjash.assistant;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/assistant")
public class InventoryAiController {
    private final InventoryAssistant assistant;
    InventoryAiController(InventoryAssistant assistant) { this.assistant=assistant; }
    @PostMapping InventoryAssistant.Reply ask(@Valid @RequestBody ApiTypes.AskRequest in) { return assistant.ask(in.question()); }
}
