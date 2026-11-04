package com.zimo.regtech.ontology.controller;

import com.zimo.regtech.common.result.R;
import com.zimo.regtech.ontology.entity.OntologyClass;
import com.zimo.regtech.ontology.entity.OntologyIndividual;
import com.zimo.regtech.ontology.entity.OntologyProperty;
import com.zimo.regtech.ontology.service.OntologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class OntologyController {

    private final OntologyService ontologyService;

    @GetMapping("/classes")
    public R<List<OntologyClass>> tree(@RequestParam(required = false) String keyword) {
        return R.ok(ontologyService.tree(keyword));
    }

    @PostMapping("/classes")
    public R<OntologyClass> createClass(@RequestBody OntologyClass clazz) {
        return R.ok(ontologyService.createClass(clazz));
    }

    @PutMapping("/classes/{id}")
    public R<Void> updateClass(@PathVariable Long id, @RequestBody OntologyClass clazz) {
        ontologyService.updateClass(id, clazz);
        return R.ok();
    }

    @DeleteMapping("/classes/{id}")
    public R<Void> deleteClass(@PathVariable Long id) {
        ontologyService.deleteClass(id);
        return R.ok();
    }

    @GetMapping("/properties")
    public R<List<OntologyProperty>> properties(@RequestParam(required = false) Long classId) {
        return R.ok(ontologyService.properties(classId));
    }

    @PostMapping("/properties")
    public R<OntologyProperty> createProperty(@RequestBody OntologyProperty property) {
        return R.ok(ontologyService.createProperty(property));
    }

    @DeleteMapping("/properties/{id}")
    public R<Void> deleteProperty(@PathVariable Long id) {
        ontologyService.deleteProperty(id);
        return R.ok();
    }

    @GetMapping("/individuals")
    public R<List<OntologyIndividual>> individuals(@RequestParam(required = false) Long classId,
                                                   @RequestParam(required = false) String keyword) {
        return R.ok(ontologyService.individuals(classId, keyword));
    }

    @PostMapping("/individuals")
    public R<OntologyIndividual> createIndividual(@RequestBody OntologyIndividual individual) {
        return R.ok(ontologyService.createIndividual(individual));
    }

    @PutMapping("/individuals/{id}")
    public R<Void> updateIndividual(@PathVariable Long id, @RequestBody OntologyIndividual individual) {
        ontologyService.updateIndividual(id, individual);
        return R.ok();
    }

    @DeleteMapping("/individuals/{id}")
    public R<Void> deleteIndividual(@PathVariable Long id) {
        ontologyService.deleteIndividual(id);
        return R.ok();
    }

    @PostMapping("/rag/ask")
    public R<Map<String, Object>> rag(@RequestBody Map<String, Object> body) {
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        int topK = body.get("topK") == null ? 6 : Integer.parseInt(String.valueOf(body.get("topK")));
        return R.ok(ontologyService.rag(question, topK));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(ontologyService.stats());
    }
}
