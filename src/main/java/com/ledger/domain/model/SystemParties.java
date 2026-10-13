package com.ledger.domain.model;

import java.util.UUID;

public final class SystemParties {

    public static final UUID TREASURY_CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID TREASURY_USD_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID TREASURY_EUR_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    public static final UUID TREASURY_LKR_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    public static final String TREASURY_EMAIL = "treasury@ledger.internal";

    private SystemParties() {
    }
}
