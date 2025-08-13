package com.example.recognitionapi.controller;

import com.example.recognitionapi.model.*;
import com.example.recognitionapi.service.RecognitionService;
import com.example.recognitionapi.service.NotificationService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.List;

@Controller
public class MessageController {

    private RecognitionService dataService;
    private NotificationService notificationService;

    // A thread-safe sink to broadcast new recognitions to subscribers
    private final Sinks.Many<Recognition> recognitionSink = Sinks.many().multicast().onBackpressureBuffer();

    public MessageController(RecognitionService dataService, NotificationService notificationService) {
        this.dataService = dataService;
        this.notificationService = notificationService;
    }

    private Employee getEmployeeFromAuthentication(Authentication authentication) {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String email = oidcUser.getEmail();
        return dataService.findByEmail(email);
    }


    // ========= Queries =========
    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public List<Team> teams() {
        return dataService.getAllTeams();
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public List<Employee> employees() {
        return dataService.getAllEmployees();
    }

    @QueryMapping
    public List<Recognition> recognitions(@Argument String recipientId, Authentication authentication) {
        Employee currentUser = getEmployeeFromAuthentication(authentication);

        // Adding a safety check. If the user can't be found, return an empty list.
        if (currentUser == null) {
            return java.util.Collections.emptyList();
        }

        // Fetching the initial, unfiltered list of recognitions from the service.
        List<Recognition> allRecognitions = dataService.getRecognitionsForRecipient(recipientId);

        // Applying the exact same security filter we use for subscriptions.
        // We stream the list and keep only the items that return 'true' from isVisibleTo.
        return allRecognitions.stream()
                .filter(recognition -> isVisibleTo(recognition, currentUser))
                .toList();
    }

    @QueryMapping
    public List<Recognition> myRecognitions(Authentication authentication) {
        Employee currentUser = getEmployeeFromAuthentication(authentication);

        if (currentUser == null) {
            // This prevents errors and returns no data if the user isn't in our system.
            return java.util.Collections.emptyList();
        }

        List<Recognition> myRecognitions = dataService.getRecognitionsForRecipient(currentUser.id());

        return myRecognitions;
    }

    @QueryMapping
    public List<Recognition> sentRecognitions(Authentication authentication) {
        Employee currentUser = getEmployeeFromAuthentication(authentication);

        if (currentUser == null) {
            return java.util.Collections.emptyList();
        }

        List<Recognition> sentRecognitions = dataService.getRecognitionsForSender(currentUser.id());

        return sentRecognitions;
    }


    // ========= Mutations =========


    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public Recognition createRecognition(
            @Argument String recipientId,
            @Argument String message,
            @Argument Visibility visibility,
            @Argument boolean isAnonymous,Authentication authentication
    ) {

        Employee sender = getEmployeeFromAuthentication(authentication);
        if (sender == null) {
            throw new IllegalStateException("Authenticated user could not be found in the system.");
        }

        if (sender.id().equals(recipientId)) {         // sender can't recognize themselves
            throw new IllegalArgumentException("You cannot send a recognition to yourself. Please select another colleague.");
        }

        // The recipient object for the notification message
        Employee recipient = dataService.getEmployeeById(recipientId);
        if (recipient == null) {
            throw new IllegalArgumentException("Recipient with ID " + recipientId + " not found.");
        }

        Recognition newRecognition = Recognition.create(sender.id(), recipientId, message,  visibility, isAnonymous);
        dataService.saveRecognition(newRecognition);

        // Pushing to the real-time subscription sink
        recognitionSink.tryEmitNext(newRecognition);

        // This is gor trigerring the webhook
        notificationService.sendRecognitionNotification(newRecognition, sender, recipient);

        return newRecognition;
    }



    // ========= Subscriptions =========

    @SubscriptionMapping
    @PreAuthorize("isAuthenticated()")
    public Flux<Recognition> recognitionReceived(Authentication authentication) {
        Employee currentUser = getEmployeeFromAuthentication(authentication);

        if (currentUser == null) {
            return Flux.empty();
        }

        return recognitionSink.asFlux()
                .filter(recognition -> isVisibleTo(recognition, currentUser));
    }


    // ========= Schema Mappings for Nested Fields =========
    @SchemaMapping(typeName = "Team", field = "members")
    public List<Employee> getTeamMembers(Team team) {
        return dataService.getAllEmployees().stream()
                .filter(employee -> employee.teamId().equals(team.id()))
                .toList();
    }

    @SchemaMapping(typeName = "Employee", field = "team")
    public Team getEmployeeTeam(Employee employee) {
        return dataService.getTeamById(employee.teamId());
    }

    @SchemaMapping(typeName = "Recognition", field = "sender")
    public Employee getRecognitionSender(Recognition recognition, Authentication authentication) {
        Employee currentUser = getEmployeeFromAuthentication(authentication);
        //if the sender is anonymous, then only admin and hr can access the sender details
        if (currentUser != null && (currentUser.role() == Role.ADMIN || currentUser.role() == Role.HR)) {
            return dataService.getEmployeeById(recognition.senderId());
        }

        if (recognition.isAnonymous()) {
            return null;
        }
        return dataService.getEmployeeById(recognition.senderId());
    }

    @SchemaMapping(typeName = "Recognition", field = "recipient")
    public Employee getRecognitionRecipient(Recognition recognition) {
        return dataService.getEmployeeById(recognition.recipientId());
    }

    private boolean isVisibleTo(Recognition recognition, Employee user) {
        // Rule 1: Admins and HR can see everything.
        if (user.role() == Role.ADMIN || user.role() == Role.HR) {
            return true;
        }

        // Rule 2: If the recognition is PUBLIC, everyone can see it.
        if (recognition.visibility() == Visibility.PUBLIC) {
            return true;
        }

        // Rule 3: If the recognition is PRIVATE, only the sender and the recipient can see it. And also HR and Admin
        if (recognition.visibility() == Visibility.PRIVATE) {
            return user.id().equals(recognition.senderId()) || user.id().equals(recognition.recipientId());
        }

        // Default to deny access if no other rule matches.
        return false;
    }
}