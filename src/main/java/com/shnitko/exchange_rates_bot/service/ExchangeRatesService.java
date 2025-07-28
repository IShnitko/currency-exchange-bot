package com.shnitko.exchange_rates_bot.service;

import com.shnitko.exchange_rates_bot.exception.ServiceException;

public interface ExchangeRatesService {
    double getExchangeRate(String currency) throws ServiceException;
    double convert(String fromCur, String toCur, double value) throws ServiceException;
    boolean isCurrencyAvailable(String currencyCode) throws ServiceException;
}
