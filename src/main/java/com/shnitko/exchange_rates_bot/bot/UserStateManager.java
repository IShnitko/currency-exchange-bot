package com.shnitko.exchange_rates_bot.bot;

import com.shnitko.exchange_rates_bot.model.UserState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserStateManager {

    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, String> chosenCurrencies = new ConcurrentHashMap<>();

    public void setState(Long chatId, UserState state) {
        userStates.put(chatId, state);
    }

    public UserState getState(Long chatId) {
        return userStates.getOrDefault(chatId, null);
    }

    public void clearState(Long chatId) {
        userStates.remove(chatId);
    }

    public void setFromCur(Long chatId, String currency) { // validation is in handleCustomFromCurrencyInput
        chosenCurrencies.put(chatId + 'F', currency);
    }

    public String getFromCur(Long chatId) {
        return chosenCurrencies.get(chatId + 'F');
    }

    public void setToCur(Long chatId, String currency) {
        chosenCurrencies.put(chatId + 'T', currency);
    }

    public String getToCur(Long chatId) {
        return chosenCurrencies.get(chatId + 'T');
    }

    public void clearFromCurOptions(Long chatId) {
        chosenCurrencies.remove(chatId + 'F');
    }

    public void clearToCurOptions(Long chatId) {
        chosenCurrencies.remove(chatId + 'T');
    }

    public void clearUserData(Long chatId) {
        clearState(chatId);
        clearFromCurOptions(chatId);
        clearToCurOptions(chatId);
    }

}