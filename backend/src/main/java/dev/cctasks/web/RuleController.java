package dev.cctasks.web;

import java.util.List;

import dev.cctasks.rule.Rule;
import dev.cctasks.rule.RuleService;
import dev.cctasks.web.Dtos.CombinedRulesResponse;
import dev.cctasks.web.Dtos.CreateRuleRequest;
import dev.cctasks.web.Dtos.ReorderRulesRequest;
import dev.cctasks.web.Dtos.RuleResponse;
import dev.cctasks.web.Dtos.UpdateRuleRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * すべての Claude Code 環境に効かせたい共通ルール。
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** 表示順の全ルール(無効なものも含む。画面で編集するため)。 */
    @GetMapping
    public List<RuleResponse> list() {
        return ruleService.list().stream().map(RuleResponse::from).toList();
    }

    /** 有効なルールを表示順に連結した 1 本の Markdown。貼り付け用。 */
    @GetMapping("/combined")
    public CombinedRulesResponse combined() {
        return new CombinedRulesResponse(ruleService.combined());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@Valid @RequestBody CreateRuleRequest request) {
        Rule created = ruleService.create(request.title(), request.body(), request.enabled());
        return RuleResponse.from(created);
    }

    /** 並び替え。全ルールの id を望む順で送ると、並び替え後の全件を返す。 */
    @PutMapping("/order")
    public List<RuleResponse> reorder(@Valid @RequestBody ReorderRulesRequest request) {
        return ruleService.reorder(request.ids()).stream().map(RuleResponse::from).toList();
    }

    @PatchMapping("/{id}")
    public RuleResponse update(@PathVariable long id, @RequestBody UpdateRuleRequest request) {
        Rule updated = ruleService.update(id, request.title(), request.body(), request.enabled());
        return RuleResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        ruleService.delete(id);
    }
}
