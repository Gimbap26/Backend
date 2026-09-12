package com.moneyweather.domain;

public final class Enums {
    private Enums() {
    }

    public enum UserStatus { ACTIVE, INACTIVE }
    public enum CategoryType { INCOME, EXPENSE, TRANSFER }
    public enum TransactionType { INCOME, EXPENSE, TRANSFER }
    public enum Direction { INFLOW, OUTFLOW }
    public enum EventType { CARD_BILL, TELECOM, SUBSCRIPTION, LOAN, SALARY, TRANSFER, ETC }
    public enum EventStatus { SCHEDULED, PAID, CANCELED }
    public enum RecurrenceType { MONTHLY, WEEKLY, YEARLY }
    public enum WeatherStatus { SUNNY, CLOUDY, RAINY, STORM }
    public enum RiskLevel { GOOD, CAUTION, DANGER }
}
