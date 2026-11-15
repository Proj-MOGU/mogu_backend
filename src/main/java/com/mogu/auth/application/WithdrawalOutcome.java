package com.mogu.auth.application;

public sealed interface WithdrawalOutcome {
    record Withdrawn() implements WithdrawalOutcome {}

    record AlreadyWithdrawn() implements WithdrawalOutcome {}
}
