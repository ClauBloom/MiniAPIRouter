package com.miniapi.router.core.intent;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class IntentResult {
    private String intent;
    private int score;
    private String reasoning;
    @Builder.Default
    private boolean specialIntent = false;

    public static IntentResult special(String intent, int score, String reasoning) {
        IntentResult r = builder().intent(intent).score(score).reasoning(reasoning).build();
        r.specialIntent = true;
        return r;
    }
}
