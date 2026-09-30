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
    public enum WeatherStatus {
        SUNNY("맑음"), CLOUDY("흐림"), RAINY("비"), STORM("폭풍");

        private final String label;

        WeatherStatus(String label) {
            this.label = label;
        }

        /** 사용자에게 보여줄 한국어 이름. */
        public String label() {
            return label;
        }
    }
    public enum RiskLevel { GOOD, CAUTION, DANGER }
}
