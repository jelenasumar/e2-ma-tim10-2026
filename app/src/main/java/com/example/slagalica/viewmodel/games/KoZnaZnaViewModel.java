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
import com.example.slagalica.data.remote.KoZnaZnaMatchDataSource;
import com.example.slagalica.data.repository.KoZnaZnaMatchRepository;
import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.KoZnaZnaMatch;
import com.example.slagalica.model.KoZnaZnaQuestion;
import com.example.slagalica.model.KoZnaZnaScoring;
import com.example.slagalica.model.KoZnaZnaUiState;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.utils.AvatarImageLoader;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Collections;
import java.util.List;

public class KoZnaZnaViewModel extends AndroidViewModel {

    private final KoZnaZnaMatchRepository matchRepository;
    private final UserProfileRepository profileRepository;
    private final RoomSessionRepository roomRepository = new RoomSessionRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTickRunnable = this::onTimerTick;

    private final MutableLiveData<KoZnaZnaUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private ListenerRegistration matchListener;
    private ListenerRegistration roomListener;
    private String matchId;
    private String myUid;
    private String activeRoomId = "";
    private boolean isHost;
    private boolean iHaveAnswered;
    private boolean statsRecorded;
    private boolean roomMatchCreationStarted;
    private boolean resolveInFlight;
    private boolean presenceMarkInFlight;
    private int selectedAnswerIndex = KoZnaZnaUiState.NO_SELECTION;
    private int myHits;
    private int myMisses;
    private int roomBaseHostScore;
    private int roomBaseGuestScore;
    private String localStatusMessage = "";
    private String hostAvatarUri = "";
    private String guestAvatarUri = "";
    private String observedHostUid = "";
    private String observedGuestUid = "";
    private String lastPublishedAvatarUri = "";
    private ListenerRegistration hostAvatarListener;
    private ListenerRegistration guestAvatarListener;
    private String displayedQuestionKey = "";
    private long displayedQuestionStartedAtMs = 0L;
    private long displayedQuestionEndsAtMs = 0L;
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
        this.resolveInFlight = false;
        this.presenceMarkInFlight = false;
        this.selectedAnswerIndex = KoZnaZnaUiState.NO_SELECTION;
        this.myHits = 0;
        this.myMisses = 0;
        if (activeRoomId.isEmpty()) {
            this.roomBaseHostScore = 0;
            this.roomBaseGuestScore = 0;
        }
        this.hostAvatarUri = "";
        this.guestAvatarUri = "";
        this.observedHostUid = "";
        this.observedGuestUid = "";
        this.lastPublishedAvatarUri = "";
        this.displayedQuestionKey = "";
        this.displayedQuestionStartedAtMs = 0L;
        this.displayedQuestionEndsAtMs = 0L;
        this.localStatusMessage = getApplication().getString(R.string.kzz_waiting_sync);

        if (matchListener != null) {
            matchListener.remove();
        }

        mainHandler.removeCallbacks(timerTickRunnable);
        matchListener = matchRepository.listenMatch(
                matchId,
                this::onMatchUpdated,
                error -> errorMessage.setValue(error)
        );
        mainHandler.post(timerTickRunnable);
    }

    public void startRoomGame(@NonNull String roomId) {
        if (roomId.isEmpty() || roomId.equals(activeRoomId)) {
            return;
        }
        activeRoomId = roomId;
        localStatusMessage = getApplication().getString(R.string.kzz_waiting_sync);

        profileRepository.ensureAuthenticated(
                () -> {
                    String uid = matchRepository.getCurrentUid();
                    if (uid == null) {
                        errorMessage.setValue(getApplication().getString(R.string.error_guest_sign_in));
                        return;
                    }
                    myUid = uid;
                    if (roomListener != null) {
                        roomListener.remove();
                    }
                    roomListener = roomRepository.listenRoom(
                            roomId,
                            this::onRoomSessionUpdated,
                            error -> errorMessage.setValue(error)
                    );
                },
                error -> errorMessage.setValue(error)
        );
    }

    private void onRoomSessionUpdated(@NonNull RoomSession room) {
        roomBaseHostScore = room.getHostTotalScore();
        roomBaseGuestScore = room.getGuestTotalScore();
        String existingMatchId = room.getKoZnaZnaMatchId();
        if (!existingMatchId.isEmpty()) {
            if (roomListener != null) {
                roomListener.remove();
                roomListener = null;
            }
            if (matchListener == null || !existingMatchId.equals(matchId)) {
                startOnlineMatch(existingMatchId, myUid);
            }
            return;
        }

        if (!myUid.equals(room.getHostUid()) || roomMatchCreationStarted) {
            return;
        }
        if (room.getGuestUid().isEmpty()) {
            localStatusMessage = getApplication().getString(R.string.kzz_waiting_opponent);
            publishWaitingState(room);
            return;
        }

        roomMatchCreationStarted = true;
        List<Integer> questionOrder = KoZnaZnaMatchDataSource.shuffledQuestionOrderStatic(
                KoZnaZnaMatchDataSource.QUESTIONS_PER_MATCH
        );
        matchRepository.createMatchFromRoom(
                room.getRoomId(),
                room.getHostUid(),
                room.getHostUsername(),
                room.getGuestUid(),
                room.getGuestUsername(),
                questionOrder,
                createdMatchId -> { },
                error -> {
                    roomMatchCreationStarted = false;
                    errorMessage.setValue(error);
                }
        );
    }

    private void publishWaitingState(@NonNull RoomSession room) {
        uiState.setValue(new KoZnaZnaUiState(
                1,
                KoZnaZnaMatchDataSource.QUESTIONS_PER_MATCH,
                0,
                0,
                room.getHostTotalScore(),
                room.getGuestTotalScore(),
                room.getHostUsername(),
                room.getGuestUsername(),
                "",
                "",
                "",
                Collections.emptyList(),
                KoZnaZnaUiState.NO_SELECTION,
                false,
                false,
                localStatusMessage,
                false
        ));
        syncAvatarsFromMatch(null, room.getHostUid(), room.getGuestUid());
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

        long answeredAtMs = localQuestionElapsedMillis();
        KoZnaZnaQuestion question = currentQuestion(latestMatch);
        if (question != null && question.isCorrect(selectedAnswerIndex)) {
            myHits++;
        } else {
            myMisses++;
        }

        iHaveAnswered = true;
        publishFromMatch(latestMatch);
        matchRepository.submitAnswer(
                matchId,
                myUid,
                latestMatch.getHostUid(),
                selectedAnswerIndex,
                answeredAtMs,
                () -> {
                    if (latestMatch != null) {
                        publishFromMatch(latestMatch);
                        if (isHost) {
                            maybeResolveQuestion(latestMatch);
                        }
                    }
                },
                error -> errorMessage.setValue(error)
        );
    }

    public void skipQuestion() {
        if (!canAnswerLocally() || latestMatch == null) {
            return;
        }
        iHaveAnswered = true;
        publishFromMatch(latestMatch);
        long answeredAtMs = localQuestionElapsedMillis();
        matchRepository.submitAnswer(
                matchId,
                myUid,
                latestMatch.getHostUid(),
                KoZnaZnaScoring.ANSWER_SKIP,
                answeredAtMs,
                () -> {
                    if (latestMatch != null) {
                        publishFromMatch(latestMatch);
                        if (isHost) {
                            maybeResolveQuestion(latestMatch);
                        }
                    }
                },
                error -> errorMessage.setValue(error)
        );
    }

    public void finishAndExit() {
        if (matchListener != null) {
            matchListener.remove();
            matchListener = null;
        }
        if (roomListener != null) {
            roomListener.remove();
            roomListener = null;
        }
        removeAvatarListeners();
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
        syncAvatarsFromMatch(match, match.getHostUid(), match.getGuestUid());
        publishMyAvatarToMatch(match);
        markPlayerPresentIfNeeded(match);

        int previousQuestionIndex = latestMatch != null ? latestMatch.getCurrentQuestionIndex() : -1;
        if (match.getCurrentQuestionIndex() != previousQuestionIndex) {
            resolveInFlight = false;
            iHaveAnswered = false;
            selectedAnswerIndex = KoZnaZnaUiState.NO_SELECTION;
            if (latestMatch != null) {
                localStatusMessage = buildResolutionMessage(latestMatch);
            }
        } else {
            int myAnswerIndex = myUid.equals(match.getHostUid())
                    ? match.getHostAnswerIndex()
                    : match.getGuestAnswerIndex();
            if (myAnswerIndex != KoZnaZnaScoring.ANSWER_PENDING) {
                iHaveAnswered = true;
            }
        }
        if (latestMatch != null
                && KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus())
                && !KoZnaZnaMatch.STATUS_FINISHED.equals(latestMatch.getStatus())) {
            localStatusMessage = buildResolutionMessage(latestMatch);
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
        if (!isHost || resolveInFlight || KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus())) {
            return;
        }

        if (match.isQuestionResolved()) {
            if (match.getQuestionStartedAtMs() == 0L) {
                return;
            }
            resolveInFlight = true;
            matchRepository.advanceQuestion(
                    matchId,
                    unused -> resolveInFlight = false,
                    error -> {
                        resolveInFlight = false;
                        errorMessage.setValue(error);
                    }
            );
            return;
        }

        if (match.getQuestionStartedAtMs() <= 0L) {
            return;
        }
        boolean hostPending = match.getHostAnswerIndex() == KoZnaZnaScoring.ANSWER_PENDING;
        boolean guestPending = match.getGuestAnswerIndex() == KoZnaZnaScoring.ANSWER_PENDING;
        boolean bothAnswered = !hostPending && !guestPending;
        boolean timeUp = localQuestionRemainingMillis() <= KoZnaZnaMatchDataSource.RESOLVE_GRACE_MS;

        if (!bothAnswered && !timeUp) {
            return;
        }

        KoZnaZnaQuestion question = currentQuestion(match);
        if (question == null) {
            return;
        }

        resolveInFlight = true;
        matchRepository.tryResolveQuestion(
                matchId,
                question.getCorrectIndex(),
                unused -> resolveInFlight = false,
                error -> {
                    resolveInFlight = false;
                    errorMessage.setValue(error);
                }
        );
    }

    private void onTimerTick() {
        if (latestMatch != null) {
            markPlayerPresentIfNeeded(latestMatch);
            publishFromMatch(latestMatch);
            if (isHost && KoZnaZnaMatch.STATUS_PLAYING.equals(latestMatch.getStatus())) {
                maybeResolveQuestion(latestMatch);
            }
        }
        mainHandler.postDelayed(timerTickRunnable, 250L);
    }

    private void publishFromMatch(@Nullable KoZnaZnaMatch match) {
        if (match == null) {
            return;
        }

        boolean waitingForStart = match.getQuestionStartedAtMs() <= 0L;
        updateDisplayedQuestionClock(match);
        int totalQuestions = match.getQuestionOrder().isEmpty()
                ? KoZnaZnaMatchDataSource.QUESTIONS_PER_MATCH
                : match.getQuestionOrder().size();
        int questionSlotSeconds = KoZnaZnaMatchDataSource.QUESTION_MS / 1000;
        int questionLeft = waitingForStart
                ? questionSlotSeconds
                : secondsFromMillis(localQuestionRemainingMillis());
        int remainingQuestionSlots = Math.max(0, totalQuestions - match.getCurrentQuestionIndex() - 1);
        int roundLeft = waitingForStart
                ? totalQuestions * questionSlotSeconds
                : remainingQuestionSlots * questionSlotSeconds + questionLeft;

        KoZnaZnaQuestion question = currentQuestion(match);
        boolean showQuestion = !waitingForStart || KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus());
        String questionText = "";
        List<String> options = Collections.emptyList();
        if (showQuestion && question != null) {
            questionText = question.getText();
            options = question.getOptions();
        }

        boolean finished = KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus());
        boolean canAnswer = canAnswerLocally() && !finished;

        String status = !match.getStatusMessage().isEmpty()
                ? match.getStatusMessage()
                : localStatusMessage;
        if (waitingForStart && !finished && !canAnswer && !iHaveAnswered) {
            status = getApplication().getString(R.string.kzz_waiting_sync);
        }

        if (finished && status.isEmpty()) {
            status = getApplication().getString(
                    R.string.kzz_game_finished,
                    displayHostScore(match),
                    displayGuestScore(match)
            );
        }

        uiState.setValue(new KoZnaZnaUiState(
                Math.min(match.getCurrentQuestionIndex() + 1, totalQuestions),
                totalQuestions,
                roundLeft,
                questionLeft,
                displayHostScore(match),
                displayGuestScore(match),
                match.getHostUsername(),
                match.getGuestUsername(),
                hostAvatarUri,
                guestAvatarUri,
                questionText,
                options,
                selectedAnswerIndex,
                canAnswer,
                finished,
                status,
                false
        ));
    }

    private void syncAvatarsFromMatch(
            @Nullable KoZnaZnaMatch match,
            @NonNull String hostUid,
            @NonNull String guestUid
    ) {
        if (match != null) {
            if (!match.getHostAvatarUri().isEmpty()) {
                hostAvatarUri = displayAvatarUri(match.getHostAvatarUri(), myUid.equals(hostUid));
            }
            if (!match.getGuestAvatarUri().isEmpty()) {
                guestAvatarUri = displayAvatarUri(match.getGuestAvatarUri(), myUid.equals(guestUid));
            }
        }
        applyLocalAvatarOverride(hostUid, guestUid);
        listenPlayerAvatars(hostUid, guestUid);
    }

    private void listenPlayerAvatars(@NonNull String hostUid, @NonNull String guestUid) {
        if (hostUid.equals(observedHostUid) && guestUid.equals(observedGuestUid)) {
            return;
        }
        observedHostUid = hostUid;
        observedGuestUid = guestUid;
        removeAvatarListeners();
        hostAvatarListener = profileRepository.listenAvatarUriForUser(
                hostUid,
                uri -> onProfileAvatarChanged(true, hostUid, guestUid, uri),
                error -> { }
        );
        guestAvatarListener = profileRepository.listenAvatarUriForUser(
                guestUid,
                uri -> onProfileAvatarChanged(false, hostUid, guestUid, uri),
                error -> { }
        );
    }

    private void onProfileAvatarChanged(
            boolean hostSlot,
            @NonNull String hostUid,
            @NonNull String guestUid,
            @Nullable String avatarUri
    ) {
        boolean isCurrentUser = hostSlot ? myUid.equals(hostUid) : myUid.equals(guestUid);
        String resolved = displayAvatarUri(avatarUri, isCurrentUser);
        if (hostSlot) {
            hostAvatarUri = resolved;
        } else {
            guestAvatarUri = resolved;
        }
        applyLocalAvatarOverride(hostUid, guestUid);
        publishCurrentAvatarToMatchIfNeeded(hostSlot, avatarUri != null ? avatarUri : "");
        if (latestMatch != null) {
            publishFromMatch(latestMatch);
        }
    }

    private void removeAvatarListeners() {
        if (hostAvatarListener != null) {
            hostAvatarListener.remove();
            hostAvatarListener = null;
        }
        if (guestAvatarListener != null) {
            guestAvatarListener.remove();
            guestAvatarListener = null;
        }
    }

    private void publishMyAvatarToMatch(@NonNull KoZnaZnaMatch match) {
        if (matchId == null || matchId.isEmpty() || myUid.isEmpty()) {
            return;
        }
        profileRepository.ensurePublicAvatarUri(
                avatarUri -> publishAvatarToMatchIfNeeded(match, avatarUri),
                error -> { }
        );
    }

    private void publishAvatarToMatchIfNeeded(@NonNull KoZnaZnaMatch match, @NonNull String avatarUri) {
        if (avatarUri.isEmpty()) {
            return;
        }
        String existing = myUid.equals(match.getHostUid())
                ? match.getHostAvatarUri()
                : match.getGuestAvatarUri();
        if (avatarUri.equals(existing) || avatarUri.equals(lastPublishedAvatarUri)) {
            lastPublishedAvatarUri = avatarUri;
            return;
        }
        lastPublishedAvatarUri = avatarUri;
        matchRepository.updatePlayerAvatar(
                matchId,
                myUid,
                match.getHostUid(),
                avatarUri,
                () -> { },
                error -> lastPublishedAvatarUri = ""
        );
    }

    private void publishCurrentAvatarToMatchIfNeeded(boolean hostSlot, @NonNull String avatarUri) {
        if (latestMatch == null
                || matchId == null
                || matchId.isEmpty()
                || myUid.isEmpty()
                || avatarUri.isEmpty()
                || !AvatarImageLoader.isSharedAvatarUri(avatarUri)) {
            return;
        }
        boolean isMine = hostSlot
                ? myUid.equals(latestMatch.getHostUid())
                : myUid.equals(latestMatch.getGuestUid());
        if (!isMine) {
            return;
        }
        publishAvatarToMatchIfNeeded(latestMatch, avatarUri);
    }

    private void applyLocalAvatarOverride(@NonNull String hostUid, @NonNull String guestUid) {
        String localAvatar = localAvatarUri(profileRepository.loadProfile().getAvatarUri());
        if (localAvatar.isEmpty()) {
            return;
        }
        if (myUid.equals(hostUid)) {
            hostAvatarUri = localAvatar;
        } else if (myUid.equals(guestUid)) {
            guestAvatarUri = localAvatar;
        }
    }

    @NonNull
    private static String displayAvatarUri(@Nullable String avatarUri, boolean isCurrentUser) {
        if (avatarUri == null || avatarUri.isEmpty()) {
            return "";
        }
        if (isCurrentUser || AvatarImageLoader.isSharedAvatarUri(avatarUri)) {
            return avatarUri;
        }
        return "";
    }

    @NonNull
    private static String localAvatarUri(@Nullable String avatarUri) {
        return avatarUri != null ? avatarUri : "";
    }

    private void markPlayerPresentIfNeeded(@NonNull KoZnaZnaMatch match) {
        if (match.getQuestionStartedAtMs() > 0L
                || presenceMarkInFlight
                || matchId == null
                || matchId.isEmpty()
                || myUid.isEmpty()) {
            return;
        }
        presenceMarkInFlight = true;
        matchRepository.markPlayerPresent(
                matchId,
                myUid,
                match.getHostUid(),
                () -> presenceMarkInFlight = false,
                error -> presenceMarkInFlight = false
        );
    }

    private void updateDisplayedQuestionClock(@NonNull KoZnaZnaMatch match) {
        if (match.getQuestionStartedAtMs() <= 0L) {
            displayedQuestionKey = "";
            displayedQuestionStartedAtMs = 0L;
            displayedQuestionEndsAtMs = 0L;
            return;
        }
        String questionKey = match.getMatchId() + "|" + match.getCurrentQuestionIndex();
        if (questionKey.equals(displayedQuestionKey)) {
            return;
        }
        displayedQuestionKey = questionKey;
        displayedQuestionStartedAtMs = System.currentTimeMillis();
        displayedQuestionEndsAtMs = displayedQuestionStartedAtMs + KoZnaZnaMatchDataSource.QUESTION_MS;
    }

    private long localQuestionRemainingMillis() {
        if (displayedQuestionEndsAtMs <= 0L) {
            return 0L;
        }
        return Math.min(
                Math.max(0L, displayedQuestionEndsAtMs - System.currentTimeMillis()),
                KoZnaZnaMatchDataSource.QUESTION_MS
        );
    }

    private long localQuestionElapsedMillis() {
        if (displayedQuestionStartedAtMs <= 0L) {
            return 0L;
        }
        return Math.min(
                KoZnaZnaMatchDataSource.QUESTION_MS,
                Math.max(0L, System.currentTimeMillis() - displayedQuestionStartedAtMs)
        );
    }

    private static int secondsFromMillis(long millis) {
        return (int) Math.max(0, Math.ceil(millis / 1000.0));
    }

    private boolean canAnswerLocally() {
        if (latestMatch == null
                || !KoZnaZnaMatch.STATUS_PLAYING.equals(latestMatch.getStatus())
                || iHaveAnswered
                || myUid.isEmpty()) {
            return false;
        }
        if (latestMatch.getQuestionStartedAtMs() <= 0L || localQuestionRemainingMillis() <= 0L) {
            return false;
        }
        int myAnswerIndex = myUid.equals(latestMatch.getHostUid())
                ? latestMatch.getHostAnswerIndex()
                : latestMatch.getGuestAnswerIndex();
        return myAnswerIndex == KoZnaZnaScoring.ANSWER_PENDING;
    }

    @Nullable
    private KoZnaZnaQuestion currentQuestion(@NonNull KoZnaZnaMatch match) {
        List<Integer> order = KoZnaZnaMatch.normalizeQuestionOrder(match.getQuestionOrder());
        int index = match.getCurrentQuestionIndex();
        if (index < 0 || index >= order.size()) {
            return null;
        }
        List<KoZnaZnaQuestion> questions = KoZnaZnaQuestion.defaultQuestions();
        return questions.get(order.get(index));
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
                displayHostScore(match),
                displayGuestScore(match)
        );
    }

    private int displayHostScore(@NonNull KoZnaZnaMatch match) {
        return roomBaseHostScore + match.getHostScore();
    }

    private int displayGuestScore(@NonNull KoZnaZnaMatch match) {
        return roomBaseGuestScore + match.getGuestScore();
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
