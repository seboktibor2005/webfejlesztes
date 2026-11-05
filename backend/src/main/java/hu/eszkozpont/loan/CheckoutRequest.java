package hu.eszkozpont.loan;

import jakarta.validation.constraints.*;

public record CheckoutRequest(@NotNull IssueCondition issueCondition, @NotNull @PositiveOrZero Long version) {}
