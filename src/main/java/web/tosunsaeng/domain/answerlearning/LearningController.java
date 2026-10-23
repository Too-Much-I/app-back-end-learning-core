package web.tosunsaeng.domain.answerlearning;

import jakarta.validation.Valid;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionAccess;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

@RestController
@RequestMapping("/api/v1/learning")
@ConditionalOnProperty(prefix="app.answer-learning", name="enabled", havingValue="true")
public class LearningController {
    private final CurrentUserProvider users;
    private final LearningCatalog catalog;
    private final LearningService service;
    private final ObjectProvider<DeletionAccess> deletion;
    public LearningController(CurrentUserProvider users, LearningCatalog catalog, LearningService service, ObjectProvider<DeletionAccess> deletion) {
        this.users=users; this.catalog=catalog; this.service=service; this.deletion=deletion;
    }
    private String owner() {
        String owner = LearningService.uuid(users.getCurrentUserId());
        deletion.ifAvailable(d -> d.requirePublicWrite(owner));
        return owner;
    }
    private static BaseResponse<Object> ok(Object value) { return BaseResponse.onSuccess(SuccessStatus.OK, value); }
    @GetMapping("/units") public BaseResponse<Object> list(@RequestParam int part, @RequestParam String level) { return ok(catalog.list(owner(),part,level)); }
    @GetMapping("/units/{id}") public BaseResponse<Object> detail(@PathVariable String id, @RequestParam String level) { return ok(catalog.detail(owner(),id,level)); }
    @GetMapping("/media/{id}/url") public BaseResponse<Object> media(@PathVariable String id) { return ok(catalog.media(owner(),id)); }
    @GetMapping("/capabilities") public BaseResponse<Object> capabilities(@RequestParam String lessonId) { owner(); return ok(service.capabilities(lessonId)); }
    @PostMapping("/submissions") public BaseResponse<Object> create(@Valid @RequestBody Create body) { return ok(service.create(owner(),body)); }
    @PostMapping("/submissions/{id}/upload-url") public BaseResponse<Object> upload(@PathVariable String id, @Valid @RequestBody Upload body) { return ok(service.upload(owner(),id,body)); }
    @PostMapping("/submissions/{id}/submit") public BaseResponse<Object> submit(@PathVariable String id, @RequestBody(required=false) Document body) { empty(body); return ok(service.submit(owner(),id)); }
    @GetMapping("/submissions/{id}") public BaseResponse<Object> get(@PathVariable String id) { return ok(service.get(owner(),id)); }
    @PostMapping("/submissions/{id}/retry") public BaseResponse<Object> retry(@PathVariable String id, @RequestBody(required=false) Document body) { empty(body); return ok(service.retry(owner(),id)); }
    @PutMapping("/lessons/{id}/completion") public BaseResponse<Object> complete(@PathVariable String id, @Valid @RequestBody Completion body) { return ok(service.complete(owner(),id,body)); }
    private static void empty(Document body) { if (body != null && !body.isEmpty()) throw LearningFailure.bad(); }
}
