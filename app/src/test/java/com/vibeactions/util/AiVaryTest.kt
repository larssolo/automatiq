package com.vibeactions.util

import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVaryTest {

    @Test fun applies_forScheduledAiMacroOwnFire() {
        assertTrue(
            aiVariationApplies(
                TriggerType.SCHEDULED, aiReplyEnabled = true,
                hasOverrideBody = false, hasOverrideRecipient = false
            )
        )
    }

    @Test fun applies_forManualAiMacro() {
        // The editor only offers the toggle for SCHEDULED, but an imported file can carry it on a
        // MANUAL macro — varying there is the intended generalisation, not an error.
        assertTrue(
            aiVariationApplies(
                TriggerType.MANUAL, aiReplyEnabled = true,
                hasOverrideBody = false, hasOverrideRecipient = false
            )
        )
    }

    @Test fun neverApplies_whenAiDisabled() {
        assertFalse(
            aiVariationApplies(
                TriggerType.SCHEDULED, aiReplyEnabled = false,
                hasOverrideBody = false, hasOverrideRecipient = false
            )
        )
    }

    @Test fun neverApplies_forReplyTriggers() {
        // INCOMING has its own AI pipeline (GeminiReplyWorker); MISSED_CALL never uses AI.
        assertFalse(
            aiVariationApplies(
                TriggerType.INCOMING, aiReplyEnabled = true,
                hasOverrideBody = false, hasOverrideRecipient = false
            )
        )
        assertFalse(
            aiVariationApplies(
                TriggerType.MISSED_CALL, aiReplyEnabled = true,
                hasOverrideBody = false, hasOverrideRecipient = false
            )
        )
    }

    @Test fun neverApplies_withOverrideBody() {
        // An override body IS the generated/approved text — re-varying it would loop forever.
        assertFalse(
            aiVariationApplies(
                TriggerType.SCHEDULED, aiReplyEnabled = true,
                hasOverrideBody = true, hasOverrideRecipient = false
            )
        )
    }

    @Test fun neverApplies_withOverrideRecipient() {
        // A reply fire (fallback paths pass the counterparty) must send as-is.
        assertFalse(
            aiVariationApplies(
                TriggerType.SCHEDULED, aiReplyEnabled = true,
                hasOverrideBody = false, hasOverrideRecipient = true
            )
        )
    }

    @Test fun varyPrompt_pinsTheContract() {
        val prompt = buildVaryPrompt(null)
        assertTrue(prompt.contains("ONE fresh variation"))
        assertTrue(prompt.contains("same meaning"))
        assertTrue(prompt.contains("ONLY with the message itself"))
        assertFalse(prompt.contains("Style guidance"))
        assertFalse(prompt.contains("null"))
    }

    @Test fun varyPrompt_appendsInstruction_ignoresBlank() {
        assertTrue(buildVaryPrompt("varm og uformel").endsWith("Style guidance: varm og uformel"))
        assertFalse(buildVaryPrompt("   ").contains("Style guidance"))
        assertFalse(buildVaryPrompt("").contains("Style guidance"))
    }
}
