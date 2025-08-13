package com.example.recognitionapi.model;

import java.util.List;

public record Team(String id, String name, List<Employee> members) {
}