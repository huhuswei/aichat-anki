package com.ss.aianki;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class DeckPreferences {
    private static final String PREF_NAME = "DeckPreferences";
    private static final String KEY_HIDDEN_DECKS = "hidden_decks";
    
    private final SharedPreferences prefs;
    
    public DeckPreferences(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public boolean isDeckHidden(long deckId) {
        Set<String> hiddenDecks = getHiddenDeckIds();
        return hiddenDecks.contains(String.valueOf(deckId));
    }
    
    public void setDeckHidden(long deckId, boolean hidden) {
        Set<String> hiddenDecks = getHiddenDeckIds();
        
        if (hidden) {
            hiddenDecks.add(String.valueOf(deckId));
        } else {
            hiddenDecks.remove(String.valueOf(deckId));
        }
        
        prefs.edit().putStringSet(KEY_HIDDEN_DECKS, hiddenDecks).apply();
    }
    
    public Set<String> getHiddenDeckIds() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN_DECKS, new HashSet<>()));
    }
    
    public void clearHiddenDecks() {
        prefs.edit().remove(KEY_HIDDEN_DECKS).apply();
    }
}
