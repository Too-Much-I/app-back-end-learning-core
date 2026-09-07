package web.tosunsaeng.domain.challenge;

import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;
import static web.tosunsaeng.domain.challenge.ChallengeViews.*;

public class ChallengeService {
    private final ChallengeStore store;
    private final ChallengeTransactions tx;
    private final ChallengeCatalog catalog;
    private final ChallengeAudioStorage audio;
    private final Clock clock;
    public ChallengeService(ChallengeStore store, ChallengeTransactions tx, ChallengeCatalog catalog,
                            ChallengeAudioStorage audio, Clock clock) {
        this.store = store; this.tx = tx; this.catalog = catalog; this.audio = audio; this.clock = clock;
    }
    public Attempt expire(Attempt observed) {
        if (observed.state != State.CREATED || clock.instant().isBefore(observed.submissionDeadlineAt)) return observed;
        return tx.run(observed.userId, () -> {
            Attempt a = store.owned(observed.id, observed.userId);
            if (a.state == State.CREATED && !clock.instant().isBefore(a.submissionDeadlineAt)) {
                a.state = State.EXPIRED; a.expiredAt = a.submissionDeadlineAt; store.save(a);
            }
            return a;
        });
    }
    private List<Attempt> range(String owner, String from, String to) {
        return store.range(owner, from, to).stream().map(this::expire).toList();
    }
    private static Attempt at(List<Attempt> attempts, int q) {
        return attempts.stream().filter(a -> a.questionNumber == q).findFirst().orElse(null);
    }
    private static int solved(List<Attempt> attempts) {
        return (int) attempts.stream().filter(a -> a.state == State.SUBMITTED).count();
    }
    private static void previous(List<Attempt> attempts, int q) {
        for (int n = 1; n < q; n++) {
            Attempt prior = at(attempts, n);
            if (prior == null || !prior.terminal()) throw new ChallengeFailure(409, "CHALLENGE_PREVIOUS_QUESTION_INCOMPLETE");
        }
    }
    public Progress today(String owner) {
        DateInfo date = catalog.dateInfo(); catalog.questions(LocalDate.parse(date.challengeDate()));
        List<Attempt> attempts = range(owner, date.challengeDate(), date.challengeDate());
        List<Integer> complete = attempts.stream().filter(Attempt::terminal).map(a -> a.questionNumber).sorted().toList();
        Integer next = IntStream.rangeClosed(1, 3).boxed().filter(n -> !complete.contains(n)).findFirst().orElse(null);
        List<QuestionStatus> questions = IntStream.rangeClosed(1, 3).mapToObj(n -> {
            Attempt a = at(attempts, n); boolean terminal = a != null && a.terminal();
            return new QuestionStatus(n, terminal ? "submitted" : "not_started", a == null ? "not_requested" : a.gradingStatus, terminal);
        }).toList();
        return new Progress(date.challengeDate(), date.challengeDateExpiresAt(), date.expiresInSeconds(),
                complete.size() == 3 ? "completed" : attempts.isEmpty() ? "not_started" : "in_progress", 3, next, complete, questions);
    }
    public QuestionView question(String owner, int q, String date) {
        ChallengeCatalog.number(q); catalog.requireToday(date);
        List<Question> questions = catalog.questions(LocalDate.parse(date));
        List<Attempt> attempts = range(owner, date, date); previous(attempts, q);
        Attempt a = at(attempts, q); Question snapshot = a == null ? questions.get(q - 1) : a.question;
        catalog.requireToday(date);
        return new QuestionView(date, q, 3, snapshot.korean(), snapshot.difficulty(),
                a != null && a.terminal() ? "submitted" : "not_started", a == null ? "not_requested" : a.gradingStatus);
    }
    public Start start(String owner, int q, String date) {
        ChallengeCatalog.number(q); catalog.requireToday(date);
        List<Question> questions = catalog.questions(LocalDate.parse(date));
        previous(range(owner, date, date), q);
        Attempt observed = store.question(owner, date, q);
        if (observed != null) { observed = expire(observed); if (observed.terminal()) throw already(); return ChallengeViews.start(observed); }
        try {
            return tx.run(owner, () -> {
                catalog.requireToday(date);
                previous(store.range(owner, date, date), q);
                Attempt existing = store.question(owner, date, q);
                if (existing != null) {
                    if (existing.terminal() || !clock.instant().isBefore(existing.submissionDeadlineAt)) throw already();
                    return ChallengeViews.start(existing);
                }
                Instant now = clock.instant();
                // Date and createdAt are chosen from the same server instant, not response delivery time.
                if (!LocalDate.ofInstant(now, ChallengeCatalog.KST).toString().equals(date)) catalog.requireToday(date);
                return ChallengeViews.start(store.insert(Attempt.create(UUID.randomUUID().toString(), owner, date, questions.get(q - 1), now)));
            });
        } catch (RuntimeException failure) {
            if (failure instanceof ChallengeFailure) throw failure;
            Attempt winner = store.question(owner, date, q);
            if (winner != null) {
                winner = expire(winner); if (winner.terminal()) throw already(); return ChallengeViews.start(winner);
            }
            throw ChallengeFailure.internal();
        }
    }
    public UploadResponse upload(String owner, String id) {
        Attempt a = expire(store.owned(ChallengeCatalog.uuid(id), owner)); requireSubmittable(a);
        return new UploadResponse(a.id, a.submissionDeadlineAt, audio.upload(a, clock.instant()));
    }
    public Submission submit(String owner, int q, String id, String key) {
        ChallengeCatalog.number(q); String attemptId = ChallengeCatalog.uuid(id), normalizedKey = ChallengeCatalog.uuid(key);
        Attempt a = store.owned(attemptId, owner);
        if (a.questionNumber != q) throw ChallengeFailure.badRequest();
        Submission replay = replay(owner, normalizedKey, attemptId, q);
        if (replay != null) return replay;
        a = expire(a); requireSubmittable(a);
        audio.validate(a.uploadKey); // No network I/O inside the transaction.
        try {
            Submission response = tx.run(owner, () -> {
                Submission existing = replay(owner, normalizedKey, attemptId, q);
                if (existing != null) return existing;
                Attempt current = store.owned(attemptId, owner);
                if (current.state == State.EXPIRED) return null;
                if (current.state == State.SUBMITTED) throw already();
                Instant now = clock.instant();
                if (!now.isBefore(current.submissionDeadlineAt)) {
                    current.state = State.EXPIRED; current.expiredAt = current.submissionDeadlineAt; store.save(current); return null;
                }
                current.state = State.SUBMITTED; current.submittedAt = now; current.generation = 1; current.gradingStatus = "pending";
                store.save(current); store.insert(Job.pending(current, now));
                SubmitReceipt receipt = new SubmitReceipt(); receipt.id = owner + ":" + normalizedKey;
                receipt.userId = owner; receipt.idempotencyKey = normalizedKey; receipt.attemptId = attemptId;
                receipt.questionNumber = q; receipt.response = ChallengeViews.submitted(current); store.insert(receipt);
                return receipt.response;
            });
            if (response == null) throw expired(); return response;
        } catch (RuntimeException failure) {
            if (failure instanceof ChallengeFailure) throw failure;
            // A commit acknowledgement may be lost; only a complete durable success is replayable.
            Submission recovered = replay(owner, normalizedKey, attemptId, q);
            if (recovered != null) return recovered;
            throw ChallengeFailure.internal();
        }
    }
    private Submission replay(String owner, String key, String id, int q) {
        SubmitReceipt receipt = store.receipt(owner, key);
        if (receipt == null) return null;
        if (!id.equals(receipt.attemptId) || receipt.questionNumber != q) throw new ChallengeFailure(409, "CHALLENGE_IDEMPOTENCY_CONFLICT");
        Attempt attempt = store.owned(id, owner);
        if (attempt.state != State.SUBMITTED || store.job(Job.id(id, 1)) == null || receipt.response == null) throw ChallengeFailure.internal();
        return receipt.response;
    }
    public History history(String owner, String value) {
        YearMonth month;
        try {
            if (value != null && !value.matches("[0-9]{4}-[0-9]{2}")) throw ChallengeFailure.badRequest();
            month = value == null ? YearMonth.from(catalog.today()) : YearMonth.parse(value);
        } catch (DateTimeException e) { throw ChallengeFailure.badRequest(); }
        LocalDate today = catalog.today(), base = catalog.baseDate();
        if (month.isAfter(YearMonth.from(today))) throw ChallengeFailure.badRequest();
        LocalDate from = month.atDay(1).isBefore(base) ? base : month.atDay(1);
        LocalDate to = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        if (from.isAfter(to)) return new History(month.toString(), List.of());
        List<Attempt> attempts = range(owner, from.toString(), to.toString()); List<Day> dates = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String date = d.toString(); int count = solved(attempts.stream().filter(a -> date.equals(a.challengeDate)).toList());
            dates.add(new Day(date, count > 0, count));
        }
        return new History(month.toString(), dates);
    }
    public Object results(String owner, String date, Integer q) {
        LocalDate requested = ChallengeCatalog.date(date);
        if (requested.isAfter(catalog.today())) throw ChallengeFailure.badRequest();
        if (q != null) ChallengeCatalog.number(q);
        List<Attempt> attempts = range(owner, date, date);
        return q == null ? new Count(date, solved(attempts)) : new Results(date, solved(attempts), detail(at(attempts, q)));
    }
    static void requireSubmittable(Attempt a) { if (a.state == State.EXPIRED) throw expired(); if (a.state == State.SUBMITTED) throw already(); }
    static ChallengeFailure already() { return new ChallengeFailure(409, "CHALLENGE_ALREADY_ATTEMPTED"); }
    static ChallengeFailure expired() { return new ChallengeFailure(410, "CHALLENGE_ATTEMPT_EXPIRED"); }
}
