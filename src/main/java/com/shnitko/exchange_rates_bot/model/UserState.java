package com.shnitko.exchange_rates_bot.model;

public enum UserState {
    AWAITING_VALUE_INPUT,
    AWAITING_FROM_CUR,
    AWAITING_TO_CUR,
    AWAITING_FROM_CUSTOM_CUR,
    AWAITING_TO_CUSTOM_CUR
}
