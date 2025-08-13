package com.example.recognitionapi.service;

import com.example.recognitionapi.model.Employee;
import com.example.recognitionapi.model.Recognition;
import com.example.recognitionapi.model.Visibility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;


import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final WebClient webClient = WebClient.create();

    // Reading configuration from application.properties
    private final boolean isSlackEnabled;
    private final String slackWebhookUrl;
    private final boolean isTeamsEnabled;
    private final String teamsWebhookUrl;

    public NotificationService(
            @Value("${app.webhook.slack.enabled:false}") boolean isSlackEnabled,
            @Value("${app.webhook.slack.url:}") String slackWebhookUrl,
            @Value("${app.webhook.teams.enabled:false}") boolean isTeamsEnabled,
            @Value("${app.webhook.teams.url:}") String teamsWebhookUrl
    ) {
        this.isSlackEnabled = isSlackEnabled;
        this.slackWebhookUrl = slackWebhookUrl;
        this.isTeamsEnabled = isTeamsEnabled;
        this.teamsWebhookUrl = teamsWebhookUrl;
    }


    public void sendRecognitionNotification(Recognition recognition, Employee sender, Employee recipient) {
        // You can add business logic here. For example, only send notifications for PUBLIC recognitions.
        if (recognition.visibility() != Visibility.PUBLIC) {
            System.out.println("--- NOTIFICATION: Skipping webhook for PRIVATE recognition " + recognition.id() + " ---");
            return;
        }

        if (isSlackEnabled && slackWebhookUrl != null && !slackWebhookUrl.isEmpty()) {
            sendSlackNotification(recognition, sender, recipient);
        }

        if (isTeamsEnabled && teamsWebhookUrl != null && !teamsWebhookUrl.isEmpty()) {
            sendTeamsNotification(recognition, sender, recipient);
        }
    }

    private void sendSlackNotification(Recognition recognition, Employee sender, Employee recipient) {
        String senderName = recognition.isAnonymous() ? "An anonymous colleague" : sender.name();
        String message = String.format(
                "*%s* sent a new recognition to *%s*! :tada:\n> %s",
                senderName,
                recipient.name(),
                recognition.message()
        );

        Map<String, String> payload = Map.of("text", message);

        System.out.println("--- NOTIFICATION: Sending payload to Slack webhook ---");
        webClient.post()
                .uri(slackWebhookUrl)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity() // We don't care about the response body, just that it was successful
                .doOnSuccess(response -> System.out.println("--- NOTIFICATION: Slack webhook sent successfully! Status: " + response.getStatusCode() + " ---"))
                .doOnError(error -> System.err.println("--- NOTIFICATION ERROR: Failed to send Slack webhook: " + error.getMessage() + " ---"))
                .subscribe(); // .subscribe() makes the call asynchronous (non-blocking)
    }

    private void sendTeamsNotification(Recognition recognition, Employee sender, Employee recipient) {
        String senderName = recognition.isAnonymous() ? "An anonymous colleague" : sender.name();
        String title = String.format("New Recognition for %s!", recipient.name());
        String summary = String.format("%s sent a recognition to %s", senderName, recipient.name());

        Map<String, Object> payload = Map.of(
                "@type", "MessageCard",
                "@context", "http://schema.org/extensions",
                "themeColor", "0076D7", // A nice blue color
                "summary", summary,
                "sections", List.of(
                        Map.of(
                                "activityTitle", title,
                                "activitySubtitle", String.format("From: **%s**", senderName),
                                "facts", List.of(
                                        Map.of("name", "Recipient:", "value", recipient.name()),
                                        Map.of("name", "Message:", "value", recognition.message())
                                ),
                                "markdown", true
                        )
                )
        );

        System.out.println("--- NOTIFICATION: Sending payload to Teams webhook ---");
        sendWebhook(teamsWebhookUrl, payload, "Teams");
    }

    private void sendWebhook(String url, Object payload, String serviceName) {
        webClient.post()
                .uri(url)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> System.out.println("--- NOTIFICATION: " + serviceName + " webhook sent successfully! Status: " + response.getStatusCode() + " ---"))
                .doOnError(error -> System.err.println("--- NOTIFICATION ERROR: Failed to send " + serviceName + " webhook: " + error.getMessage() + " ---"))
                .subscribe();
    }

    private boolean hasUrl(String url) {
        return url != null && !url.isEmpty();
    }
}
