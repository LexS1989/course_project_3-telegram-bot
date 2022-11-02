package pro.sky.telegrambot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    private final TelegramBot telegramBot;

    private final NotificationTaskRepository notificationTaskRepository;

    private final Pattern PATTERN = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)([\\W+]+)");

    private final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");


    public TelegramBotUpdatesListener(NotificationTaskRepository notificationTaskRepository, TelegramBot telegramBot) {
        this.notificationTaskRepository = notificationTaskRepository;
        this.telegramBot = telegramBot;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);
            if (update.message().text().equals("/start")) {
                sendWelcome(update);
            } else {
                createTask(update);
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    /**
     * Вывод приветственного сообщения по команде /start
     */
    private void sendWelcome(Update update) {
        logger.info("Method sendWelcome started");
        SendMessage textMessage = new SendMessage(update.message().chat().id(),
                "Приветствую, я бот для напоминания" +
                        "\nВевдите дату, время и текст сообщения по образцу:" +
                        "\n\"31.12.1999 23:45 Пора за стол\"");
        telegramBot.execute(textMessage);
    }

    /**
     * Парсим сообщение с создаваемой задачей и сохраняем в БД
     */
    public void createTask(Update update) {
        logger.info("Method createTask started");
        String textMessage = update.message().text();

        Matcher matcher = PATTERN.matcher(textMessage);
        if (matcher.matches()) {

            // обрабатываем ситуацию, когда строка соответствует паттерну
            NotificationTask notificationTask = new NotificationTask();
            String date = matcher.group(1);
            String textTask = matcher.group(3);
            LocalDateTime dateTime = LocalDateTime.parse(date, FORMAT);

            // создаем на основе полученных от пользователя данных сущность и сохранять ее в БД
            notificationTask.setDateTime(dateTime);
            notificationTask.setNotificationText(textTask);
            notificationTask.setIdChat(update.message().chat().id());
            notificationTaskRepository.save(notificationTask);

            SendMessage returnMessage = new SendMessage(update.message().chat().id(),
                    "Данные сохранены успешно");
            telegramBot.execute(returnMessage);

            logger.info("Метод createTask выполнен успешно");
        } else {
            SendMessage returnMessage = new SendMessage(update.message().chat().id(),
                    "Данные не сохранены, проверьте правильность ввода");
            telegramBot.execute(returnMessage);

            logger.warn("Данные не сохранены");
        }
    }

    /**
     * Выбор записей из БД и отправка сообщений с использованием шедулера раз в минуту
    */
    @Scheduled(cron = "0 0/1 * * * *")
    public void sendReminder() {
        logger.info("Method createTask started");

        LocalDateTime timeNow = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        List<NotificationTask> tasks = notificationTaskRepository.findNotificationTaskByDateTime(timeNow);
        if (!tasks.isEmpty()) {
            tasks.forEach(task -> {
                Long chatId = task.getIdChat();
                String message = task.getNotificationText();
                SendMessage sendMessage = new SendMessage(chatId, message);
                telegramBot.execute(sendMessage);
            });
        } else {
            logger.info("Лист задач пуст");
        }
    }
}
