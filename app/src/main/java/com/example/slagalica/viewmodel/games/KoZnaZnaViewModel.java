package com.example.slagalica.viewmodel.games;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.KoZnaZnaMatchRepository;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.KoZnaZnaMatch;
import com.example.slagalica.model.KoZnaZnaQuestion;
import com.example.slagalica.model.KoZnaZnaScoring;
import com.example.slagalica.model.KoZnaZnaUiState;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class KoZnaZnaViewModel extends AndroidViewModel {

    private static final int TOTAL_QUESTIONS = 5;

    private final KoZnaZnaMatchRepository matchRepository;
    private final UserProfileRepository profileRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTickRunnable = this::onTimerTick;

    private final MutableLiveData<KoZnaZnaUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private ListenerRegistration matchListener;
    private String matchId;
    private String myUid;
    private boolean isHost;
    private boolean iHaveAnswered;
    private boolean statsRecorded;
    private boolean advancingQuestion;
    private int lastScheduledAdvanceIndex = -1;
    private int selectedAnswerIndex = KoZnaZnaUiState.NO_SELECTION;
    private int myHits;
    private int myMisses;
    private String localStatusMessage = "";
    @Nullable
    private KoZnaZnaMatch latestMatch;

    public KoZnaZnaViewModel(@NonNull Application application) {
        super(application);
        matchRepository = new KoZnaZnaMatchRepository();
        profileRepository = new UserProfileRepository(application);
    }

    @NonNull
    public LiveData<KoZnaZnaUiState> getUiState() {
        return uiState;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void startOnlineMatch(@NonNull String matchId, @NonNull String myUid) {
        this.matchId = matchId;
        this.myUid = myUid;
        this.isHost = false;
        this.iHaveAnswered = false;
        this.statsRecorded = false;
        this.advancingQuestion = false;
        this.lastScheduledAdvanceIndex = -1;
        this.selectedAnswerIndex = KoZnaZnaUiState.NO_SELECTION;
        this.myHits = 0;
        this.myMisses = 0;
        this.localStatusMessage = getApplication().getString(R.string.kzz_waiting_sync);

        if (matchListener != null) {
            matchListener.remove();
        }

        matchListener = matchRepository.listenMatch(
                matchId,
                this::onMatchUpdated,
                error -> errorMessage.setValue(error)
        );
        mainHandler.post(timerTickRunnable);
    }

    public void selectAnswer(int answerIndex) {
        if (!canAnswerLocally()) {
            return;
        }
        selectedAnswerIndex = answerIndex;
        publishFromMatch(latestMatch);
    }

    public void submitAnswer() {
        if (!canAnswerLocally() || latestMatch == null) {
            return;
        }
        if (selectedAnswerIndex < 0) {
            errorMessage.setValue(getApplication().getString(R.string.kzz_select_answer));
            return;
        }

        long answeredAtMs = System.currentTimeMillis() - latestMatch.getQuestionStartedAtMs();
        KoZnaZnaQuestion question = currentQuestion(latestMatch);
        if (question != null && question.isCorrect(selectedAnswerIndex)) {
            myHits++;
        } else {
            myMisses++;
        }

        iHaveAnswered = true;
        matchRepository.submitAnswer(
                matchId,
                isHost,
                selectedAnswerIndex,
                answeredAtMs,
                () -> publishFromMatch(latestMatch),
                error -> errorMessage.setValue(error)
        );
    }

    public void skipQuestion() {
        if (!canAnswerLocally() || latestMatch == null) {
            return;
        }
        iHaveAnswered = true;
        long answeredAtMs = System.currentTimeMillis() - latestMatch.getQuestionStartedAtMs();
        matchRepository.submitAnswer(
                matchId,
                isHost,
                KoZnaZnaScoring.ANSWER_SKIP,
                answeredAtMs,
                () -> publishFromMatch(latestMatch),
                error -> errorMessage.setValue(error)
        );
    }

    public void finishAndExit() {
        if (matchListener != null) {
            matchListener.remove();
            matchListener = null;
        }
        mainHandler.removeCallbacks(timerTickRunnable);
    }

    @Override
    protected void onCleared() {
        finishAndExit();
        super.onCleared();
    }

    private void onMatchUpdated(@NonNull KoZnaZnaMatch match) {
        if (myUid.isEmpty()) {
            myUid = matchRepository.getCurrentUid() != null ? matchRepository.getCurrentUid() : myUid;
        }
        isHost = myUid.equals(match.getHostUid());

        if (match.getCurrentQuestionIndex() != (latestMatch != null ? latestMatch.getCurrentQuestionIndex() : -1)
                || (latestMatch != null && latestMatch.isQuestionResolved() && !match.isQuestionResolved())) {
            iHaveAnswered = false;
            selectedAnswerIndex = KoZnaZnaUiState.NO_SELECTION;
            localStatusMessage = "";
        }

        latestMatch = match;
        publishFromMatch(match);

        if (KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus())) {
            recordStatsIfNeeded(match);
            return;
        }

        if (isHost) {
            maybeResolveQuestion(match);
        }
    }

    private void maybeResolveQuestion(@NonNull KoZnaZnaMatch match) {
        if (match.isQuestionResolved() || advancingQuestion) {
            if (match.isQuestionResolved() && !advancingQuestion && !KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus())) {
                scheduleAdvanceIfHost(match);
            }
            return;
        }

        long now = System.currentTimeMillis();
        boolean hostPending = match.getHostAnswerIndex() == KoZnaZnaScoring.ANSWER_PENDING;
        boolean guestPending = match.getGuestAnswerIndex() == KoZnaZnaScoring.ANSWER_PENDING;
        boolean timeUp = now >= match.getQuestionEndsAtMs();

        if (hostPending && guestPending && !timeUp) {
            return;
        }
        if (!hostPending && guestPending && !timeUp) {
            return;
        }
        if (hostPending && !guestPending && !timeUp) {
            return;
        }

        KoZnaZnaQuestion question = currentQuestion(match);
        if (question == null) {
            return;
        }

        matchRepository.tryResolveQuestion(
                matchId,
                question.getCorrectIndex(),
                resolved -> {
                    localStatusMessage = buildResolutionMessage(resolved);
                    matchRepository.updateStatusMessage(matchId, localStatusMessage, () -> { }, error -> { });
                    scheduleAdvanceIfHost(resolved);
                },
                error -> errorMessage.setValue(error)
        );
    }

    private void scheduleAdvanceIfHost(@NonNull KoZnaZnaMatch match) {
        if (!isHost || advancingQuestion || KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus())) {
            return;
        }
        if (lastScheduledAdvanceIndex == match.getCurrentQuestionIndex()) {
            return;
        }
        lastScheduledAdvanceIndex = match.getCurrentQuestionIndex();
        advancingQuestion = true;
        mainHandler.postDelayed(() -> matchRepository.advanceQuestion(
                matchId,
                advanced -> {
                    advancingQuestion = false;
                    localStatusMessage = "";
                },
                error -> {
                    advancingQuestion = false;
                    errorMessage.setValue(error);
                }
        ), 1200L);
    }

    private void onTimerTick() {
        if (latestMatch != null) {
            publishFromMatch(latestMatch);
            if (isHost && !latestMatch.isQuestionResolved()
                    && KoZnaZnaMatch.STATUS_PLAYING.equals(latestMatch.getStatus())) {
                maybeResolveQuestion(latestMatch);
            }
        }
        mainHandler.postDelayed(timerTickRunnable, 250L);
    }

    private void publishFromMatch(@Nullable KoZnaZnaMatch match) {
        if (match == null) {
            return;
        }

        long now = System.currentTimeMillis();
        int roundLeft = (int) Math.max(0, Math.ceil((match.getRoundEndsAtMs() - now) / 1000.0));
        int questionLeft = (int) Math.max(0, Math.ceil((match.getQuestionEndsAtMs() - now) / 1000.0));

        KoZnaZnaQuestion question = currentQuestion(match);
        List<String> options = question != null
                ? question.getOptions()
                : KoZnaZnaQuestion.defaultQuestions().get(0).getOptions();

        boolean finished = KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus());
        boolean canAnswer = canAnswerLocally() && !match.isQuestionResolved() && questionLeft > 0 && !finished;

        int myAnswerIndex = isHost ? match.getHostAnswerIndex() : match.getGuestAnswerIndex();
        int opponentAnswerIndex = isHost ? match.getGuestAnswerIndex() : match.getHostAnswerIndex();
        boolean waitingOpponent = iHaveAnswered
                && opponentAnswerIndex == KoZnaZnaScoring.ANSWER_PENDING
                && !match.isQuestionResolved();

        String status = !match.getStatusMessage().isEmpty()
                ? match.getStatusMessage()
                : localStatusMessage;
        if (waitingOpponent) {
            status = getApplication().getString(R.string.kzz_waiting_opponent);
        }

        if (finished && status.isEmpty()) {
            status = getApplication().getString(
                    R.string.kzz_game_finished,
                    match.getHostScore(),
                    match.getGuestScore()
            );
        }

        uiState.setValue(new KoZnaZnaUiState(
                Math.min(match.getCurrentQuestionIndex() + 1, TOTAL_QUESTIONS),
                TOTAL_QUESTIONS,
                roundLeft,
                questionLeft,
                match.getHostScore(),
                match.getGuestScore(),
                match.getHostUsername(),
                match.getGuestUsername(),
                question != null ? question.getText() : "",
                options,
                selectedAnswerIndex,
                canAnswer,
                finished,
                status,
                waitingOpponent
        ));
    }

    private boolean canAnswerLocally() {
        return latestMatch != null
                && KoZnaZnaMatch.STATUS_PLAYING.equals(latestMatch.getStatus())
                && !iHaveAnswered
                && !latestMatch.isQuestionResolved();
    }

    @Nullable
    private KoZnaZnaQuestion currentQuestion(@NonNull KoZnaZnaMatch match) {
        List<Integer> order = match.getQuestionOrder();
        int index = match.getCurrentQuestionIndex();
        if (index < 0 || index >= order.size()) {
            return null;
        }
        List<KoZnaZnaQuestion> questions = KoZnaZnaQuestion.defaultQuestions();
        int questionIndex = order.get(index);
        if (questionIndex < 0 || questionIndex >= questions.size()) {
            return null;
        }
        return questions.get(questionIndex);
    }

    @NonNull
    private String buildResolutionMessage(@NonNull KoZnaZnaMatch match) {
        boolean hostAnswered = match.getHostAnswerIndex() != KoZnaZnaScoring.ANSWER_PENDING;
        boolean guestAnswered = match.getGuestAnswerIndex() != KoZnaZnaScoring.ANSWER_PENDING;
        if (!hostAnswered && !guestAnswered) {
            return getApplication().getString(R.string.kzz_no_answers);
        }

        KoZnaZnaQuestion question = currentQuestion(match);
        if (question == null) {
            return "";
        }

        Boolean hostCorrect = KoZnaZnaScoring.correctness(match.getHostAnswerIndex(), question.getCorrectIndex());
        Boolean guestCorrect = KoZnaZnaScoring.correctness(match.getGuestAnswerIndex(), question.getCorrectIndex());

        if (Boolean.TRUE.equals(hostCorrect) && Boolean.TRUE.equals(guestCorrect)) {
            if (match.getHostAnsweredAtMs() <= match.getGuestAnsweredAtMs()) {
                return getApplication().getString(R.string.kzz_both_correct_player_one);
            }
            return getApplication().getString(R.string.kzz_both_correct_player_two);
        }

        return getApplication().getString(
                R.string.kzz_question_resolved,
                match.getHostScore(),
                match.getGuestScore()
        );
    }

    private void recordStatsIfNeeded(@NonNull KoZnaZnaMatch match) {
        if (statsRecorded || !profileRepository.isRegisteredPlayer()) {
            return;
        }
        statsRecorded = true;
        int myScore = isHost ? match.getHostScore() : match.getGuestScore();
        profileRepository.recordKoZnaZnaRound(myScore, myHits, myMisses);
    }
}
