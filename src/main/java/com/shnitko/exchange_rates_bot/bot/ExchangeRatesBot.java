package com.shnitko.exchange_rates_bot.bot;

import com.shnitko.exchange_rates_bot.exception.ServiceException;
import com.shnitko.exchange_rates_bot.model.UserState;
import com.shnitko.exchange_rates_bot.service.ExchangeRatesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExchangeRatesBot extends TelegramLongPollingBot {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeRatesBot.class);

    private static final String START = "/start";
    private static final String USD = "USD Exchange Rate";
    private static final String EUR = "EUR Exchange Rate";
    private static final String HELP = "Help";
    private static final String CONVERT = "Calculate conversion";

    private final ExchangeRatesService ratesService;

    private final UserStateManager stateManager;

    @Autowired
    public ExchangeRatesBot(@Value("${bot.token}") String botToken, UserStateManager stateManager, ExchangeRatesService ratesServices) {
        super(botToken);
        this.stateManager = stateManager;
        this.ratesService = ratesServices;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            try {
                handleCallbackQuery(update.getCallbackQuery());
            } catch (TelegramApiException e) {
                LOG.error("Error while handling callback query", e);
            }
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        var message = update.getMessage().getText();
        var chatId = update.getMessage().getChatId();

        if (stateManager.getState(chatId) != null) {
            UserState state = stateManager.getState(chatId);
            switch (state) {
                case AWAITING_TO_CUR -> inputToCurrency(chatId);
                case AWAITING_VALUE_INPUT -> {
                    try {
                        handleValueInput(chatId, message);
                    } catch (TelegramApiException e) {
                        LOG.error("Error while handling value input", e);
                    }
                }
                case AWAITING_FROM_CUSTOM_CUR -> {
                    try {
                        handleCustomFromCurrencyInput(chatId, message);
                    } catch (ServiceException e) {
                        LOG.error("Error while handling custom value input", e);
                    }
                }
                case AWAITING_TO_CUSTOM_CUR -> {
                    try {
                        handleCustomToCurrencyInput(chatId, message);
                    } catch (ServiceException e) {
                        LOG.error("Error while handling custom value input", e);
                    }
                }
            }
            return;
        }

        switch (message) {
            case START -> {
                String username = update.getMessage().getChat().getUserName();
                firstLaunch(chatId, username);
            }
            case USD -> currencyCommand(chatId, "USD");
            case EUR -> currencyCommand(chatId, "EUR");
            case HELP -> helpCommand(chatId);
            case CONVERT -> inputFromCurrency(chatId);
            default -> unknownCommand(chatId);
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) throws TelegramApiException {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        LOG.info("Callback data: {}", data);
        switch (data) {
            case String s when s.charAt(0) == 'F' -> {
                LOG.info("In case F switch statement");
                switch (data.substring(2)) {
                    case "EUR" -> stateManager.setFromCur(chatId, "EUR");
                    case "USD" -> stateManager.setFromCur(chatId, "USD");
                    case "PLN" -> stateManager.setFromCur(chatId, "PLN");
                    case "CNY" -> stateManager.setFromCur(chatId, "CNY");
                    case "Custom" -> {
                        execute(DeleteMessage.builder()
                                .chatId(String.valueOf(chatId))
                                .messageId(callbackQuery.getMessage().getMessageId())
                                .build());
                        inputCustomFromCurrency(chatId);
                        return;
                    }
                    case "CustomReturn" -> {
                        inputFromCurrency(chatId);
                        return;
                    }
                    case "Return" -> {
                        stateManager.clearState(chatId);
                        execute(DeleteMessage.builder()
                                .chatId(String.valueOf(chatId))
                                .messageId(callbackQuery.getMessage().getMessageId())
                                .build());
                        sendMainMenu(chatId);
                        return;
                    }
                }
                execute(DeleteMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .messageId(callbackQuery.getMessage().getMessageId())
                        .build());

                if (!data.equals("Return")) inputToCurrency(chatId); // TODO: test if i need the if statement
            }
            case String s when s.charAt(0) == 'T' -> {
                LOG.info("In case T switch statement");
                stateManager.setState(chatId, UserState.AWAITING_VALUE_INPUT);
                switch (data.substring(2)) {
                    case "EUR" -> stateManager.setToCur(chatId, "EUR");
                    case "USD" -> stateManager.setToCur(chatId, "USD");
                    case "PLN" -> stateManager.setToCur(chatId, "PLN");
                    case "CNY" -> stateManager.setToCur(chatId, "CNY");
                    case "Custom" -> {
                        execute(DeleteMessage.builder()
                                .chatId(String.valueOf(chatId))
                                .messageId(callbackQuery.getMessage().getMessageId())
                                .build());
                        inputCustomToCurrency(chatId);
                        return;
                    }
                    case "CustomReturn" -> {
                        inputToCurrency(chatId);
                        return;
                    }
                    case "Return" -> {
                        stateManager.clearState(chatId);
                        execute(DeleteMessage.builder()
                                .chatId(String.valueOf(chatId))
                                .messageId(callbackQuery.getMessage().getMessageId())
                                .build());
                        inputFromCurrency(chatId);
                        return;
                    }
                }
                execute(DeleteMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .messageId(callbackQuery.getMessage().getMessageId())
                        .build());

                if (!data.equals("Return")) sendMessage(chatId, "Input value to convert:");
            }
            default -> {
                LOG.error("Unexpected callbackData");
                unknownCommand(chatId);
            }
        }
    }
    // TODO: Maybe add view all currencies
    private void handleValueInput(long chatId, String input) throws TelegramApiException {

        LOG.info("Message in handleValueInput: {}", input);
        if ("return".equalsIgnoreCase(input)) {
            stateManager.clearState(chatId);
            sendMainMenu(chatId);
            return;
        }

        try {
            try {
                double amount = Double.parseDouble(input);

                var fromCur = stateManager.getFromCur(chatId);
                var toCur = stateManager.getToCur(chatId);
                LOG.info("Chosen currencies: " + fromCur + " " + toCur);
                var res = ratesService.convert(fromCur, toCur, amount); // READ the getExchangeRate(Currency currency) for description fo the fix
                String finalResult = String.format("""
                        🔄 Result of conversion:
                        %.2f %s = %.2f %s
                        """, amount, fromCur, res, toCur);
                //return to main menu
                sendMessage(chatId, finalResult, createMainKeyboard());
            } catch (ServiceException e) {
                throw new ServiceException("Error while converting currencies", e);
            } finally {
                stateManager.clearState(chatId);
            } // TODO: maybe give an option to abort inputting value
        } catch (NumberFormatException | ServiceException e) {
            sendMessage(chatId, "Incorrect value, try again:");
            stateManager.setState(chatId, UserState.AWAITING_VALUE_INPUT);
        }
    }

    private void inputFromCurrency(Long chatId) {
        stateManager.setState(chatId, UserState.AWAITING_FROM_CUR);
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> firstRow = new ArrayList<>();
        firstRow.add(
                InlineKeyboardButton.builder()
                        .text("EUR\uD83C\uDDEA\uD83C\uDDFA")
                        .callbackData("F:EUR")
                        .build()
        );
        firstRow.add(
                InlineKeyboardButton.builder()
                        .text("USD\uD83C\uDDFA\uD83C\uDDF8")
                        .callbackData("F:USD")
                        .build()
        );

        List<InlineKeyboardButton> secondRow = new ArrayList<>();
        secondRow.add(
                InlineKeyboardButton.builder()
                        .text("PLN\uD83C\uDDF5\uD83C\uDDF1")
                        .callbackData("F:PLN")
                        .build()
        );
        secondRow.add(
                InlineKeyboardButton.builder()
                        .text("CNY\uD83C\uDDE8\uD83C\uDDF3")
                        .callbackData("F:CNY")
                        .build()
        );

        List<InlineKeyboardButton> thirdRow = new ArrayList<>();
        thirdRow.add(
                InlineKeyboardButton.builder()
                        .text("Custom currency")
                        .callbackData("F:Custom")
                        .build()
        );

        List<InlineKeyboardButton> returnRow = new ArrayList<>();
        returnRow.add(
                InlineKeyboardButton.builder()
                        .text("Return to main menu")
                        .callbackData("F:Return")
                        .build()
        );

        rows.add(firstRow);
        rows.add(secondRow);
        rows.add(thirdRow);
        rows.add(returnRow);

        inlineKeyboard.setKeyboard(rows);

        var text = "Choose currency to convert from:";

        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .replyMarkup(inlineKeyboard)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Error while getting from currency", e);
        }
    }

    private void inputToCurrency(Long chatId) {
        stateManager.setState(chatId, UserState.AWAITING_TO_CUR);
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> firstRow = new ArrayList<>();
        firstRow.add(
                InlineKeyboardButton.builder()
                        .text("EUR\uD83C\uDDEA\uD83C\uDDFA")
                        .callbackData("T:EUR")
                        .build()
        );
        firstRow.add(
                InlineKeyboardButton.builder()
                        .text("USD\uD83C\uDDFA\uD83C\uDDF8")
                        .callbackData("T:USD")
                        .build()
        );

        List<InlineKeyboardButton> secondRow = new ArrayList<>();
        secondRow.add(
                InlineKeyboardButton.builder()
                        .text("PLN\uD83C\uDDF5\uD83C\uDDF1")
                        .callbackData("T:PLN")
                        .build()
        );
        secondRow.add(
                InlineKeyboardButton.builder()
                        .text("CNY\uD83C\uDDE8\uD83C\uDDF3")
                        .callbackData("T:CNY")
                        .build()
        );

        List<InlineKeyboardButton> thirdRow = new ArrayList<>();
        thirdRow.add(
                InlineKeyboardButton.builder()
                        .text("Custom currency")
                        .callbackData("T:Custom")
                        .build()
        );

        List<InlineKeyboardButton> returnRow = new ArrayList<>();
        returnRow.add(
                InlineKeyboardButton.builder()
                        .text("Return to original currency")
                        .callbackData("T:Return")
                        .build()
        );

        rows.add(firstRow);
        rows.add(secondRow);
        rows.add(thirdRow);
        rows.add(returnRow);

        inlineKeyboard.setKeyboard(rows);

        var text = "Choose currency to convert to:";

        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .replyMarkup(inlineKeyboard)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Error while getting from currency", e);
        }
    }

    private void inputCustomFromCurrency(Long chatId) {
        stateManager.setState(chatId, UserState.AWAITING_FROM_CUSTOM_CUR);
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> returnRow = new ArrayList<>();
        returnRow.add(
                InlineKeyboardButton.builder()
                        .text("Return to available currencies")
                        .callbackData("F:CustomReturn")
                        .build()
        );
        rows.add(returnRow);
        inlineKeyboard.setKeyboard(rows);

        var text = "Input custom currency (e.x. TRY):";

        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .replyMarkup(inlineKeyboard)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Error while getting custom currency", e);
        }
    }

    private void inputCustomToCurrency(Long chatId) {
        stateManager.setState(chatId, UserState.AWAITING_TO_CUSTOM_CUR);
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> returnRow = new ArrayList<>();
        returnRow.add(
                InlineKeyboardButton.builder()
                        .text("Return to available currencies")
                        .callbackData("T:CustomReturn")
                        .build()
        );
        rows.add(returnRow);
        inlineKeyboard.setKeyboard(rows);

        var text = "Input custom currency (e.x. TRY):";

        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .replyMarkup(inlineKeyboard)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Error while getting custom currency", e);
        }
    }

    private void handleCustomFromCurrencyInput(long chatId, String input) throws ServiceException {

        LOG.info("Message in handleCustomFromCurrencyInput: {}", input);

        if ("return".equalsIgnoreCase(input)) {
            stateManager.clearState(chatId);
            sendMainMenu(chatId);
            return;
        }

        input = input.toUpperCase(); //TODO: delete redundant uppercases

        // here it will check if the currency is real
        if (!ratesService.isCurrencyAvailable(input)) {
            sendMessage(chatId, "Currency is incorrect. Try another one:");
            return;
        }

        stateManager.setFromCur(chatId, input);
        inputToCurrency(chatId);
    }

    private void handleCustomToCurrencyInput(long chatId, String input) throws ServiceException {

        LOG.info("Message in handleCustomToCurrencyInput: {}", input);
        if ("return".equalsIgnoreCase(input)) {
            stateManager.clearState(chatId);
            sendMainMenu(chatId);
            return;
        }

        input = input.toUpperCase();

        // here it will check if the currency is real
        if (!ratesService.isCurrencyAvailable(input)) {
            sendMessage(chatId, "Currency is incorrect. Try another one:");
            return;
        }

        stateManager.setToCur(chatId, input);
        sendMessage(chatId, "Input value to convert:");
        stateManager.setState(chatId, UserState.AWAITING_VALUE_INPUT);
    }

    @Override
    public String getBotUsername() {
        return "IShnitko_bot";
    }

    private void firstLaunch(Long chatId, String username) {
        var text = """
                Welcome to exchange rates bot, %s!
                
                Here you can get all the new exchange rates of National Polish Bank.
                
                For this use:
                USD Exchange Rate - get dollar rate
                EUR Exchange Rate - get euro rate
                
                Additional command:
                Help - getting additional info
                """;
        var formattedText = String.format(text, username);
        sendMessage(chatId, formattedText, createMainKeyboard());
    }

    private ReplyKeyboardMarkup createMainKeyboard() {
        // Create keyboard
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // First row
        KeyboardRow row1 = new KeyboardRow();
        row1.add(USD);
        row1.add(EUR);

        // Second row
        KeyboardRow row2 = new KeyboardRow();
        row2.add(HELP);
        row2.add(CONVERT);

        // Add rows to keyboard
        keyboard.add(row1);
        keyboard.add(row2);

        // Set keyboard properties
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);  // Auto-resize to fit screen
//        keyboardMarkup.setOneTimeKeyboard(true); // Hide after first use
        keyboardMarkup.setSelective(true);       // Show only to specific users
        return keyboardMarkup;
    }

    private void sendMainMenu(Long chatId) {
        sendMessage(chatId, "Choose an option:", createMainKeyboard());
    }

    private void currencyCommand(Long chatId, String currency) {
        String formattedText;
        try {
            var rate = ratesService.getExchangeRate(currency);
            var text = "%s rate for %s is %.4f";
            formattedText = String.format(text, currency, LocalDate.now(), rate);
        } catch (ServiceException e) {
            LOG.error("Error while getting {} rate", currency, e);
            formattedText = String.format("Couldn't get %s rate. Try later.", currency);

        }
        sendMessage(chatId, formattedText);
    }

    private void helpCommand(Long chatId) {
        var text = """
                Bot description:
                
                Here you can get all the new exchange rates of National Polish Bank.
                
                For this use:
                USD Exchange Rate - get dollar rate
                EUR Exchange Rate - get euro rate
                """;
        sendMessage(chatId, text);
    }

    private void unknownCommand(Long chatId) {
        var text = "Unknown command.";
        sendMessage(chatId, text);
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build();
        try {
            execute(message);
            LOG.debug("Sent message");
        } catch (TelegramApiException e) {
            LOG.error("Error while sending message", e);
        }
    }

    private void sendMessage(Long chatId, String text, ReplyKeyboardMarkup keyboardMarkup) {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .replyMarkup(keyboardMarkup)
                .text(text)
                .build();
        try {
            execute(message);
            LOG.debug("Sent message");
        } catch (TelegramApiException e) {
            LOG.error("Error while sending message", e);
        }
    }
}
