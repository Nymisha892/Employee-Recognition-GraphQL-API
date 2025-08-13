package com.example.recognitionapi.service;


import com.example.recognitionapi.model.Employee;
import com.example.recognitionapi.model.Recognition;
import com.example.recognitionapi.model.Role;
import com.example.recognitionapi.model.Team;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RecognitionService {

    private final Map<String, Team> teamData = new ConcurrentHashMap<>();
    private final Map<String, Employee> employeeData = new ConcurrentHashMap<>();
    private final Map<String, Recognition> recognitionData = new ConcurrentHashMap<>();

    public List<Team> getAllTeams() {
        return List.copyOf(teamData.values());
    }

    public List<Employee> getAllEmployees() {
        return List.copyOf(employeeData.values());
    }

    public Employee getEmployeeById(String id) {
        return employeeData.get(id);
    }

    public Team getTeamById(String id) {
        return teamData.get(id);
    }

    public List<Recognition> getRecognitionsForRecipient(String recipientId) {
        if (recipientId == null) {
            return List.copyOf(recognitionData.values());
        }
        return recognitionData.values().stream()
                .filter(r -> r.recipientId().equals(recipientId))
                .collect(Collectors.toList());
    }

    public List<Recognition> getRecognitionsForSender(String senderId) {
        if (senderId == null) {
            return List.copyOf(recognitionData.values());
        }
        return recognitionData.values().stream()
                .filter(r -> r.senderId().equals(senderId))
                .collect(Collectors.toList());
    }

    public Recognition saveRecognition(Recognition recognition) {
        recognitionData.put(recognition.id(), recognition);
        return recognition;
    }

    // This method populates our in-memory data store on startup
    @PostConstruct
    private void init() {
        Team engineering = new Team("1", "Engineering", List.of());
        Team product = new Team("2", "Product", List.of());
        teamData.put(engineering.id(), engineering);
        teamData.put(product.id(), product);

        Employee alice = new Employee("101", "Alice", "alice@corp.com", engineering.id(), Role.ADMIN);
        Employee bob = new Employee("102", "Bob", "bob@corp.com", engineering.id(), Role.EMPLOYEE);
        Employee charlie = new Employee("103", "Charlie", "charlie@corp.com", product.id(), Role.MANAGER);
        Employee diana = new Employee("104", "Diana", "diana@corp.com", product.id(), Role.EMPLOYEE);
        Employee eve = new Employee("105", "Eve", "eve@corp.com", "3", Role.HR); // No team

        employeeData.put(alice.id(), alice);
        employeeData.put(bob.id(), bob);
        employeeData.put(charlie.id(), charlie);
        employeeData.put(diana.id(), diana);
        employeeData.put(eve.id(), eve);
    }

    public Employee findByEmail(String email) {
        // Search through all employees to find one with a matching email (case-insensitive)
        return employeeData.values().stream()
                .filter(employee -> employee.email().equalsIgnoreCase(email))
                .findFirst()
                .orElse(null); // Return null if not found
    }
}

