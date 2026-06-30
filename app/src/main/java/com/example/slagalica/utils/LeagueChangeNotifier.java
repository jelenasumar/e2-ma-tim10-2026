package com.example.slagalica.utils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.example.slagalica.model.LeagueChangeEvent;

public final class LeagueChangeNotifier {

    private static final LeagueChangeNotifier INSTANCE = new LeagueChangeNotifier();
    private final SingleLiveEvent<LeagueChangeEvent> events = new SingleLiveEvent<>();

    private LeagueChangeNotifier() {
    }

    @NonNull
    public static LeagueChangeNotifier get() {
        return INSTANCE;
    }

    @MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<LeagueChangeEvent> observer) {
        events.observe(owner, observer);
    }

    public void notifyChange(@NonNull LeagueChangeEvent event) {
        events.postValue(event);
    }

    @Nullable
    public LeagueChangeEvent peekValue() {
        return events.getValue();
    }
}
